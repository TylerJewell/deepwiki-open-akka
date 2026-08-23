package io.akka.deepwiki.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.akka.deepwiki.domain.RepoIdentity;
import io.akka.deepwiki.domain.RepoType;
import io.akka.deepwiki.domain.TaskStatus;
import io.akka.deepwiki.domain.WikiCacheData;
import io.akka.deepwiki.domain.WikiTaskRequest;
import io.akka.deepwiki.domain.WikiTaskSubmitResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC-001 R11-R13. Port of tests/backend/services/test_wiki_task.py's end-to-end run, driven
 * against a real local directory (not a mock of {@code readRepoFileTree} itself, per
 * question-log row 5's finding about what the source's own equivalent test papers over).
 */
class WikiTaskOrchestratorTest {

  private static WikiTaskRequest requestFor(Path repoDir) {
    return new WikiTaskRequest(
        repoDir.toString(), RepoType.LOCAL, null, "google", null, "en", List.of(), List.of(), List.of(), List.of(), "acme", "widget", true);
  }

  private static WikiTaskOrchestrator orchestratorOver(Path tmp) {
    return new WikiTaskOrchestrator(
        new StubContentGenerationClient(), new MarkerFileRepositoryIndexer(tmp.resolve("index")), new WikiCacheStore(tmp.resolve("cache")), tmp.resolve("repo"), 4, 2, 1, 60);
  }

  @Test
  void submitCreatesJoinsThenServesFromCache(@TempDir Path tmp) throws IOException, InterruptedException {
    Files.writeString(tmp.resolve("README.md"), "hello", StandardCharsets.UTF_8);
    WikiTaskOrchestrator orchestrator = orchestratorOver(tmp);
    WikiTaskRequest request = requestFor(tmp);

    WikiTaskSubmitResult first = orchestrator.submit(request, 1000L);
    assertTrue(first.created());

    WikiTaskSubmitResult second = orchestrator.submit(request, 1001L);
    assertTrue(second.joined());
    assertEquals(first.taskId(), second.taskId());

    awaitTerminal(orchestrator, request.repoKey());
    WikiTask task = orchestrator.get(request.repoKey());
    assertEquals(TaskStatus.COMPLETED, task.status());
    assertEquals(1, task.pagesTotal());
    assertEquals(1, task.toStatus().pagesDone());

    // A cache file now exists, so a later submit short-circuits without a new task.
    WikiTaskSubmitResult third = orchestrator.submit(request, 1002L);
    assertTrue(third.fromCache());
  }

  @Test
  void indexIsOnlyBuiltOnce(@TempDir Path tmp) throws IOException, InterruptedException {
    Files.writeString(tmp.resolve("README.md"), "hello", StandardCharsets.UTF_8);
    RepositoryIndexer indexer = new MarkerFileRepositoryIndexer(tmp.resolve("index"));
    WikiTaskOrchestrator orchestrator =
        new WikiTaskOrchestrator(new StubContentGenerationClient(), indexer, new WikiCacheStore(tmp.resolve("cache")), tmp.resolve("repo"), 4, 2, 1, 60);
    RepoIdentity repo = new RepoIdentity(tmp.toString(), RepoType.LOCAL, tmp.resolve("repo").toString());
    assertTrue(!indexer.indexExists(repo));

    WikiTaskRequest request = requestFor(tmp);
    orchestrator.submit(request, 1L);
    awaitTerminal(orchestrator, request.repoKey());

    assertTrue(indexer.indexExists(repo));
  }

  @Test
  void structureDeterminationFailureFailsTheWholeTask(@TempDir Path tmp) throws IOException, InterruptedException {
    Files.writeString(tmp.resolve("README.md"), "hello", StandardCharsets.UTF_8);
    ContentGenerationClient alwaysFails =
        (request, prompt) -> {
          throw new RuntimeException("model unavailable");
        };
    WikiTaskOrchestrator orchestrator =
        new WikiTaskOrchestrator(alwaysFails, new MarkerFileRepositoryIndexer(tmp.resolve("index")), new WikiCacheStore(tmp.resolve("cache")), tmp.resolve("repo"), 4, 2, 1, 60);
    WikiTaskRequest request = requestFor(tmp);
    orchestrator.submit(request, 1L);

    awaitTerminal(orchestrator, request.repoKey());
    WikiTask task = orchestrator.get(request.repoKey());
    // Structure determination itself needs the content client -> that fails the whole task,
    // since there is no structure to generate placeholder pages for (R12).
    assertEquals(TaskStatus.FAILED, task.status());
    assertNotNull(task.toStatus().error());
  }

  @Test
  void aPageThatAlwaysFailsGetsAnErrorPlaceholderInsteadOfFailingTheTask(@TempDir Path tmp) throws IOException, InterruptedException {
    Files.writeString(tmp.resolve("README.md"), "hello", StandardCharsets.UTF_8);
    // Structure determination succeeds (the "<file_tree>" branch); every page prompt fails.
    ContentGenerationClient structureOkPagesFail =
        (request, prompt) -> {
          if (prompt.contains("<file_tree>")) {
            return new StubContentGenerationClient().generate(request, prompt);
          }
          throw new RuntimeException("model unavailable");
        };
    WikiTaskOrchestrator orchestrator =
        new WikiTaskOrchestrator(
            structureOkPagesFail, new MarkerFileRepositoryIndexer(tmp.resolve("index")), new WikiCacheStore(tmp.resolve("cache")), tmp.resolve("repo"), 4, 2, 1, 60);
    WikiTaskRequest request = requestFor(tmp);
    orchestrator.submit(request, 1L);

    awaitTerminal(orchestrator, request.repoKey());
    WikiTask task = orchestrator.get(request.repoKey());
    // R12: a page that exhausts its retries gets an error-placeholder body; the task still
    // completes rather than failing outright.
    assertEquals(TaskStatus.COMPLETED, task.status());

    WikiCacheData cached = new WikiCacheStore(tmp.resolve("cache")).read("acme", "widget", RepoType.LOCAL, "en");
    assertNotNull(cached);
    assertTrue(cached.generatedPages().values().stream().anyMatch(p -> p.content().startsWith("Error generating content:")));
  }

  private static void awaitTerminal(WikiTaskOrchestrator orchestrator, String key) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      WikiTask task = orchestrator.get(key);
      if (task != null && task.status().isTerminal()) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
  }
}
