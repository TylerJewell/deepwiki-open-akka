package io.akka.deepwiki.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SPEC-001 §5 end-to-end row: the real HTTP surface, against the running service.
 *
 * <p>The wiki cache lives under the OS temp dir (Bootstrap javadoc, question-log row 13's
 * sibling finding E-4) so it survives a real restart the way the source's own cache does —
 * which also means it survives between test runs on the same machine. A fixed owner/repo
 * would short-circuit to {@code fromCache=true} on the second run of this test, so the repo
 * name is randomized per invocation.
 */
public class WikiEndpointIntegrationTest extends TestKitSupport {

  @Test
  void submitAndPollATaskToCompletion(@TempDir Path repoDir) throws IOException {
    Files.writeString(repoDir.resolve("README.md"), "hello", StandardCharsets.UTF_8);
    String repo = "widget-" + UUID.randomUUID();

    var submit =
        httpClient
            .POST("/wiki/tasks")
            .withRequestBody(
                Map.of(
                    "repoUrl", repoDir.toString(),
                    "type", "local",
                    "owner", "acme",
                    "repo", repo,
                    "language", "en",
                    "comprehensive", true))
            .responseBodyAs(Map.class)
            .invoke();
    assertThat(submit.status().isSuccess()).isTrue();
    assertThat(submit.body()).containsEntry("created", true);
    String taskId = (String) submit.body().get("taskId");
    assertThat(taskId).isEqualTo("local_acme_" + repo);

    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              var status = httpClient.GET("/wiki/tasks/" + taskId).responseBodyAs(Map.class).invoke();
              assertThat(status.body().get("status")).isEqualTo("completed");
            });

    var cache =
        httpClient
            .GET("/api/wiki_cache?owner=acme&repo=" + repo + "&repo_type=local&language=en")
            .responseBodyAs(Map.class)
            .invoke();
    assertThat(cache.status().isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> repoInfo = (Map<String, Object>) cache.body().get("repo");
    assertThat(repoInfo).containsEntry("type", "local");

    var projects = httpClient.GET("/api/processed_projects").responseBodyAs(List.class).invoke();
    assertThat(projects.body()).isNotEmpty();

    httpClient.DELETE("/api/wiki_cache?owner=acme&repo=" + repo + "&repo_type=local&language=en").invoke();
  }
}
