package io.akka.deepwiki.api;

import akka.NotUsed;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import io.akka.deepwiki.application.WikiCacheStore;
import io.akka.deepwiki.application.WikiTask;
import io.akka.deepwiki.application.WikiTaskOrchestrator;
import io.akka.deepwiki.domain.RepoType;
import io.akka.deepwiki.domain.TaskStatus;
import io.akka.deepwiki.domain.WikiCacheData;
import io.akka.deepwiki.domain.WikiExporter;
import io.akka.deepwiki.domain.WikiPage;
import io.akka.deepwiki.domain.WikiTaskStatus;
import io.akka.deepwiki.domain.WikiTaskSubmitResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * SPEC-001 §1. Port of {@code routers/wiki.py}: export, local repo structure, wiki cache
 * CRUD, processed projects, and the task-submission/status/stream trio.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("")
public class WikiEndpoint extends AbstractHttpEndpoint {

  private final WikiTaskOrchestrator orchestrator;
  private final WikiCacheStore cacheStore;

  public WikiEndpoint(WikiTaskOrchestrator orchestrator, WikiCacheStore cacheStore) {
    this.orchestrator = orchestrator;
    this.cacheStore = cacheStore;
  }

  public record ExportRequest(String repoUrl, List<WikiPage> pages, String format) {}

  @Post("/export/wiki")
  public HttpResponse exportWiki(ExportRequest request) {
    WikiExporter.Format format = "markdown".equalsIgnoreCase(request.format()) ? WikiExporter.Format.MARKDOWN : WikiExporter.Format.JSON;
    if (!"markdown".equalsIgnoreCase(request.format()) && !"json".equalsIgnoreCase(request.format())) {
      throw HttpException.badRequest("format must be 'markdown' or 'json'");
    }
    String content = WikiExporter.export(request.repoUrl(), request.pages(), format, LocalDateTime.now());
    return HttpResponses.ok(content);
  }

  @Get("/local_repo/structure")
  public HttpResponse localRepoStructure() {
    String path = requestContext().queryParams().getString("path").orElse(null);
    if (path == null || path.isEmpty()) {
      return HttpResponses.badRequest("No path provided. Please provide a 'path' query parameter.");
    }
    File dir = new File(path);
    if (!dir.isDirectory()) {
      throw HttpException.error(StatusCodes.NOT_FOUND, "Directory not found: " + path);
    }
    try {
      List<String> fileTreeLines = new java.util.ArrayList<>();
      String[] readmeHolder = {""};
      walkLocalRepo(dir.toPath(), dir.toPath(), fileTreeLines, readmeHolder);
      fileTreeLines.sort(Comparator.naturalOrder());
      return HttpResponses.ok(Map.of("file_tree", String.join("\n", fileTreeLines), "readme", readmeHolder[0]));
    } catch (IOException e) {
      return HttpResponses.internalServerError();
    }
  }

  private void walkLocalRepo(java.nio.file.Path root, java.nio.file.Path dir, List<String> out, String[] readmeHolder) throws IOException {
    try (var stream = Files.list(dir)) {
      for (java.nio.file.Path child : stream.toList()) {
        String name = child.getFileName().toString();
        if (Files.isDirectory(child)) {
          if (name.startsWith(".") || name.equals("__pycache__") || name.equals("node_modules") || name.equals(".venv")) {
            continue;
          }
          walkLocalRepo(root, child, out, readmeHolder);
        } else {
          if (name.startsWith(".") || name.equals("__init__.py") || name.equals(".DS_Store")) {
            continue;
          }
          out.add(root.relativize(child).toString().replace('\\', '/'));
          if (name.equalsIgnoreCase("readme.md") && readmeHolder[0].isEmpty()) {
            readmeHolder[0] = Files.readString(child, StandardCharsets.UTF_8);
          }
        }
      }
    }
  }

  @Get("/api/wiki_cache")
  public HttpResponse readWikiCache() {
    var params = requestContext().queryParams();
    String owner = params.getString("owner").orElseThrow();
    String repo = params.getString("repo").orElseThrow();
    RepoType repoType = RepoType.fromWire(params.getString("repo_type").orElseThrow());
    String language = params.getString("language").orElse("en");
    WikiCacheData cached = cacheStore.read(owner, repo, repoType, language);
    return cached == null ? HttpResponses.ok((WikiCacheData) null) : HttpResponses.ok(cached);
  }

  @Delete("/api/wiki_cache")
  public HttpResponse deleteWikiCache() {
    var params = requestContext().queryParams();
    String owner = params.getString("owner").orElseThrow();
    String repo = params.getString("repo").orElseThrow();
    RepoType repoType = RepoType.fromWire(params.getString("repo_type").orElseThrow());
    String language = params.getString("language").orElse("en");

    // Port of the source's DEEPWIKI_AUTH_MODE/DEEPWIKI_AUTH_CODE gate (api/config.py:60-62,
    // api/routers/wiki.py:180-183) — the only route the source itself protects this way.
    if (WikiAuth.MODE_ENABLED) {
      String code = params.getString("authorization_code").orElse(null);
      if (code == null || !WikiAuth.CODE.equals(code)) {
        throw HttpException.unauthorized("Authorization code is invalid");
      }
    }

    boolean deleted = cacheStore.delete(owner, repo, repoType, language);
    if (!deleted) {
      throw HttpException.error(StatusCodes.NOT_FOUND, "Wiki cache not found");
    }
    return HttpResponses.ok(Map.of("message", "Wiki cache for " + owner + "/" + repo + " (" + language + ") deleted successfully"));
  }

  @Get("/api/processed_projects")
  public HttpResponse processedProjects() {
    return HttpResponses.ok(cacheStore.listProcessedProjects());
  }

  @Post("/wiki/tasks")
  public HttpResponse submitTask(WikiTaskRequestDto dto) {
    WikiTaskSubmitResult result = orchestrator.submit(dto.toDomain(), System.currentTimeMillis());
    return HttpResponses.ok(result);
  }

  @Get("/wiki/tasks")
  public HttpResponse listTasks() {
    String status = requestContext().queryParams().getString("status").orElse(null);
    List<WikiTaskStatus> active =
        orchestrator.active().stream()
            .sorted(Comparator.comparingLong(t -> t.toStatus().submittedAt()))
            .map(WikiTask::toStatus)
            .toList();
    if ("active".equals(status)) {
      return HttpResponses.ok(active);
    }
    List<WikiCacheStore.CacheEntry> completed = cacheStore.list();
    if ("completed".equals(status)) {
      return HttpResponses.ok(completed);
    }
    List<Object> combined = new java.util.ArrayList<>();
    combined.addAll(completed);
    combined.addAll(active);
    return HttpResponses.ok(combined);
  }

  @Get("/wiki/tasks/{taskId}")
  public HttpResponse getTask(String taskId) {
    WikiTask task = orchestrator.get(taskId);
    if (task == null) {
      throw HttpException.error(StatusCodes.NOT_FOUND, "Task not found");
    }
    return HttpResponses.ok(task.toStatus());
  }

  @Get("/wiki/tasks/{taskId}/stream")
  public HttpResponse streamTask(String taskId) {
    if (orchestrator.get(taskId) == null) {
      throw HttpException.error(StatusCodes.NOT_FOUND, "Task not found");
    }
    // Inclusive takeWhile stops right after the first terminal (or vanished) tick, mirroring
    // the source's `while True: ... yield done/error; return` loop (api/routers/wiki.py:277-293).
    // A task removed mid-stream (past its TTL) ends the stream rather than emitting the
    // source's explicit "task no longer available" error — documented in the README.
    Source<WikiTaskStatus, NotUsed> source =
        Source.tick(Duration.ZERO, Duration.ofSeconds(1), NotUsed.getInstance())
            .map(tick -> orchestrator.get(taskId))
            .takeWhile(t -> t != null && !t.status().isTerminal(), true)
            .filter(t -> t != null)
            .map(WikiTask::toStatus)
            .mapMaterializedValue(cancellable -> NotUsed.getInstance());
    return HttpResponses.serverSentEvents(
        source, s -> Integer.toString(s.pagesDone()), s -> s.status() == TaskStatus.COMPLETED ? "done" : s.status() == TaskStatus.FAILED ? "error" : "progress");
  }
}
