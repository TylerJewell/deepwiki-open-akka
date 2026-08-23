package io.akka.deepwiki.domain;

import java.util.List;

/** SPEC-001 §2. `repoKey` is the get-or-create-or-join identity (R11). */
public record WikiTaskRequest(
    String repoUrl,
    RepoType type,
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
    boolean comprehensive) {

  public String repoKey() {
    return type.wire() + "_" + owner + "_" + repo;
  }
}
