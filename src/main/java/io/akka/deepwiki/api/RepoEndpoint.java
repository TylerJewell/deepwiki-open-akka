package io.akka.deepwiki.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import io.akka.deepwiki.application.RepositoryIndexer;
import io.akka.deepwiki.domain.RepoIdentity;
import io.akka.deepwiki.domain.RepoType;
import java.util.Map;

/**
 * SPEC-001 §1. Port of {@code routers/repo.py}. The heartbeat-while-indexing SSE mechanism
 * (api/routers/repo.py:18-57) existed to keep a slow embedding call's connection alive; since
 * the embedding index itself is the pluggable stand-in boundary (SPEC-001 §4.1) and completes
 * immediately here, this port returns a single ready/done (or error) event instead of a
 * heartbeat stream — documented in the README's differences list.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/repo")
public class RepoEndpoint extends AbstractHttpEndpoint {

  private final RepositoryIndexer indexer;

  public RepoEndpoint(RepositoryIndexer indexer) {
    this.indexer = indexer;
  }

  public record PrepareRequest(String repoUrl, String type) {}

  @Post("/prepare")
  public HttpResponse prepare(PrepareRequest request) {
    RepoIdentity repo = identity(request.repoUrl(), request.type());
    if (indexer.indexExists(repo)) {
      return HttpResponses.ok(Map.of("event", "ready", "data", "already indexed"));
    }
    try {
      indexer.buildIndex(repo);
      return HttpResponses.ok(Map.of("event", "done", "data", "ok"));
    } catch (Exception e) {
      return HttpResponses.ok(Map.of("event", "error", "data", Map.of("error", e.getMessage())));
    }
  }

  @Get("/index/status")
  public HttpResponse indexStatus() {
    String repoUrl = requestContext().queryParams().getString("repoUrl").orElseThrow();
    String type = requestContext().queryParams().getString("type").orElse("github");
    RepoIdentity repo = identity(repoUrl, type);
    return HttpResponses.ok(Map.of("ready", indexer.indexExists(repo)));
  }

  private RepoIdentity identity(String repoUrl, String type) {
    return new RepoIdentity(repoUrl, RepoType.fromWire(type == null ? "github" : type), System.getProperty("java.io.tmpdir"));
  }
}
