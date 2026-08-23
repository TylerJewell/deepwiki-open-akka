package io.akka.deepwiki.domain;

public record RepoInfo(String owner, String repo, RepoType type, String repoUrl) {}
