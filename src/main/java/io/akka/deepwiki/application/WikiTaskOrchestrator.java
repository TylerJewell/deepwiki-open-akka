package io.akka.deepwiki.application;

import io.akka.deepwiki.domain.FileTreeReader;
import io.akka.deepwiki.domain.RepoCloner;
import io.akka.deepwiki.domain.RepoIdentity;
import io.akka.deepwiki.domain.RepoInfo;
import io.akka.deepwiki.domain.TaskStatus;
import io.akka.deepwiki.domain.WikiCacheData;
import io.akka.deepwiki.domain.WikiContentPostProcessor;
import io.akka.deepwiki.domain.WikiContentPostProcessor.RepoUrlContext;
import io.akka.deepwiki.domain.WikiPage;
import io.akka.deepwiki.domain.WikiPromptBuilder;
import io.akka.deepwiki.domain.WikiStructure;
import io.akka.deepwiki.domain.WikiStructureParser;
import io.akka.deepwiki.domain.WikiTaskRequest;
import io.akka.deepwiki.domain.WikiTaskSubmitResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * SPEC-001 R11-R13. Port of {@code TaskRegistry}/{@code generate_repo_wiki}/
 * {@code _determine_structure}/{@code _generate_pages}/{@code _generate_page_with_retry}/
 * {@code _generate_page} (api/services/wiki/tasks.py). Get-or-create-or-join by repo key,
 * cache short-circuit, bounded-concurrency page generation with per-page retry and an
 * error-placeholder fallback.
 */
public final class WikiTaskOrchestrator {

  private static final Pattern MARKDOWN_FENCE_LEAD = Pattern.compile("^```markdown\\s*", Pattern.CASE_INSENSITIVE);
  private static final Pattern MARKDOWN_FENCE_TAIL = Pattern.compile("```\\s*$");

  private final Map<String, WikiTask> tasks = new ConcurrentHashMap<>();
  private final Semaphore repoTaskSlots;
  private final int pageConcurrency;
  private final int pageRetries;
  private final long taskTtlSeconds;

  private final ContentGenerationClient contentClient;
  private final RepositoryIndexer indexer;
  private final WikiCacheStore cacheStore;
  private final Path repoRoot;
  private final Executor executor;
  private final ScheduledExecutorService scheduler;

  public WikiTaskOrchestrator(
      ContentGenerationClient contentClient,
      RepositoryIndexer indexer,
      WikiCacheStore cacheStore,
      Path repoRoot,
      int maxConcurrentTasks,
      int pageConcurrency,
      int pageRetries,
      long taskTtlSeconds) {
    this.contentClient = contentClient;
    this.indexer = indexer;
    this.cacheStore = cacheStore;
    this.repoRoot = repoRoot;
    this.repoTaskSlots = new Semaphore(Math.max(1, maxConcurrentTasks));
    this.pageConcurrency = Math.max(1, pageConcurrency);
    this.pageRetries = pageRetries;
    this.taskTtlSeconds = taskTtlSeconds;
    this.executor = Executors.newCachedThreadPool();
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
  }

  public WikiTask get(String taskId) {
    return tasks.get(taskId);
  }

  public List<WikiTask> active() {
    return tasks.values().stream().filter(t -> !t.status().isTerminal()).toList();
  }

  /** SPEC-001 R11: get-or-create-or-join, keyed by repoKey. */
  public synchronized WikiTaskSubmitResult submit(WikiTaskRequest request, long submittedAtMillis) {
    String key = request.repoKey();
    WikiTask existing = tasks.get(key);
    if (existing != null && !existing.status().isTerminal()) {
      return WikiTaskSubmitResult.joined(key, existing.status());
    }
    if (cacheStore.exists(request.owner(), request.repo(), request.type(), request.language())) {
      return WikiTaskSubmitResult.fromCache(key);
    }
    WikiTask task = new WikiTask(request, submittedAtMillis);
    tasks.put(key, task);
    CompletableFuture.runAsync(() -> runWithSlot(task), executor);
    return WikiTaskSubmitResult.created(key, task.status());
  }

  private void runWithSlot(WikiTask task) {
    try {
      repoTaskSlots.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    try {
      generateRepoWiki(task);
    } finally {
      repoTaskSlots.release();
      scheduleRemoval(task);
    }
  }

  private void scheduleRemoval(WikiTask task) {
    scheduler.schedule(
        () -> {
          if (tasks.get(task.repoKey()) == task && task.status().isTerminal()) {
            tasks.remove(task.repoKey());
          }
        },
        taskTtlSeconds,
        TimeUnit.SECONDS);
  }

  /** SPEC-001 R12: the state machine. Any exception here fails the task with its message. */
  public void generateRepoWiki(WikiTask task) {
    WikiTaskRequest r = task.request();
    try {
      RepoIdentity repo = new RepoIdentity(r.repoUrl(), r.type(), repoRoot.toString());

      if (!indexer.indexExists(repo)) {
        task.setStatus(TaskStatus.INDEXING);
        indexer.buildIndex(repo);
      }

      task.setStatus(TaskStatus.DETERMINING_STRUCTURE);
      WikiStructure structure = determineStructure(task, repo);
      task.setWikiStructure(structure);

      task.setStatus(TaskStatus.GENERATING);
      Map<String, WikiPage> pages = generatePages(task, structure);

      save(task, pages);
      task.setStatus(TaskStatus.COMPLETED);
    } catch (Exception e) {
      task.setStatus(TaskStatus.FAILED);
      task.setError(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  private WikiStructure determineStructure(WikiTask task, RepoIdentity repo) {
    WikiTaskRequest r = task.request();
    if (!repo.isLocal()) {
      Path local = Path.of(repo.savePath());
      if (!local.toFile().exists()) {
        RepoCloner.cloneFor(r.type(), r.repoUrl(), r.token(), local);
      }
    }

    Path savePath = Path.of(repo.savePath());
    String branch = FileTreeReader.detectDefaultBranch(savePath);
    task.setDefaultBranch(branch);

    // R4/R12: excluded/included bound correctly (SPEC-001 §4.2 fixes question-log rows 4/6 -
    // the source's own call site here shuffles these four arguments positionally).
    FileTreeReader.FileTree fileTree =
        FileTreeReader.readRepoFileTree(savePath, r.includedFiles(), r.includedDirs(), r.excludedFiles(), r.excludedDirs());

    String prompt =
        WikiPromptBuilder.buildStructurePrompt(r.owner(), r.repo(), fileTree.files(), fileTree.readme(), r.comprehensive(), r.language());

    String response = contentClient.generate(r, prompt);
    return WikiStructureParser.parse(response, r.comprehensive());
  }

  private Map<String, WikiPage> generatePages(WikiTask task, WikiStructure structure) {
    Semaphore pageSlots = new Semaphore(pageConcurrency);
    Map<String, WikiPage> pages = new ConcurrentHashMap<>();
    List<CompletableFuture<Void>> futures =
        structure.pages().stream()
            .map(
                page ->
                    CompletableFuture.runAsync(
                        () -> {
                          try {
                            pageSlots.acquire();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                          }
                          task.addCurrentPageId(page.id());
                          try {
                            pages.put(page.id(), generatePageWithRetry(task, page));
                          } finally {
                            task.removeCurrentPageId(page.id());
                            task.incrementPagesDone();
                            pageSlots.release();
                          }
                        },
                        executor))
            .toList();
    futures.forEach(CompletableFuture::join);
    return pages;
  }

  private WikiPage generatePageWithRetry(WikiTask task, WikiPage page) {
    Exception lastError = null;
    for (int attempt = 0; attempt <= pageRetries; attempt++) {
      try {
        return generatePage(task, page);
      } catch (Exception e) {
        lastError = e;
      }
    }
    String message = lastError == null ? "unknown error" : (lastError.getMessage() == null ? lastError.toString() : lastError.getMessage());
    return page.withContent("Error generating content: " + message);
  }

  private WikiPage generatePage(WikiTask task, WikiPage page) {
    WikiTaskRequest r = task.request();
    RepoUrlContext ctx = new RepoUrlContext(r.type(), r.repoUrl(), task.defaultBranch());
    String fileLinks =
        page.filePaths().stream()
            .map(p -> "- [" + p + "](" + WikiContentPostProcessor.generateFileUrl(p, ctx) + ")")
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    String prompt = WikiPromptBuilder.buildPagePrompt(page.title(), fileLinks, r.language());

    String content = contentClient.generate(r, prompt);
    content = MARKDOWN_FENCE_TAIL.matcher(MARKDOWN_FENCE_LEAD.matcher(content).replaceFirst("")).replaceFirst("");
    content = WikiContentPostProcessor.postProcessWikiContent(content, page.filePaths(), ctx);
    return page.withContent(content);
  }

  private void save(WikiTask task, Map<String, WikiPage> pages) {
    WikiTaskRequest r = task.request();
    WikiCacheData data =
        new WikiCacheData(
            task.wikiStructure(),
            pages,
            new RepoInfo(r.owner(), r.repo(), r.type(), r.repoUrl()),
            r.provider(),
            r.model());
    cacheStore.save(r.owner(), r.repo(), r.type(), r.language(), data);
  }
}
