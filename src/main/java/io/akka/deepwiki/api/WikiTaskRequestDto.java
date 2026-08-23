package io.akka.deepwiki.api;

import io.akka.deepwiki.domain.RepoType;
import io.akka.deepwiki.domain.WikiTaskRequest;
import java.util.List;

/** Wire shape for {@code POST /wiki/tasks} — mirrors {@code WikiTaskRequest} (api/schemas/repo.py:11-21). */
public record WikiTaskRequestDto(
    String repoUrl,
    String type,
    String token,
    String provider,
    String model,
    String language,
    List<String> excludedDirs,
    List<String> excludedFiles,
    List<String> includedDirs,
    List<String> includedFiles,
    String owner,
    String repo,
    Boolean comprehensive) {

  public WikiTaskRequest toDomain() {
    return new WikiTaskRequest(
        repoUrl,
        RepoType.fromWire(type == null ? "github" : type),
        token,
        provider == null ? "google" : provider,
        model,
        language == null ? "en" : language,
        excludedDirs == null ? List.of() : excludedDirs,
        excludedFiles == null ? List.of() : excludedFiles,
        includedDirs == null ? List.of() : includedDirs,
        includedFiles == null ? List.of() : includedFiles,
        owner,
        repo,
        comprehensive == null || comprehensive);
  }
}
