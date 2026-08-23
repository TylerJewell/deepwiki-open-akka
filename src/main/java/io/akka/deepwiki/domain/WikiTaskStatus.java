package io.akka.deepwiki.domain;

import java.util.List;

/** SPEC-001 §2. Client-facing status (never carries the access token). */
public record WikiTaskStatus(
    String id,
    String owner,
    String repo,
    RepoType repoType,
    String language,
    TaskStatus status,
    int pagesDone,
    int pagesTotal,
    List<String> currentPageIds,
    WikiStructure wikiStructure,
    String error,
    long submittedAt) {

  public String name() {
    return owner + "/" + repo;
  }
}
