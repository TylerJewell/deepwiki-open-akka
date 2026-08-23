package io.akka.deepwiki.application;

import io.akka.deepwiki.domain.RepoIdentity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SPEC-001 §4.1: deterministic stand-in for the embedding/RAG index boundary. Tracks
 * "has this repo been indexed" as a marker file under {@code indexRoot}, rather than
 * building and querying a real vector index — the wiki task state machine only ever
 * branches on the boolean, per SPEC-001 R12.
 */
public final class MarkerFileRepositoryIndexer implements RepositoryIndexer {

  private final Path indexRoot;

  public MarkerFileRepositoryIndexer(Path indexRoot) {
    this.indexRoot = indexRoot;
    try {
      Files.createDirectories(indexRoot);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean indexExists(RepoIdentity repo) {
    return Files.exists(markerFor(repo));
  }

  @Override
  public void buildIndex(RepoIdentity repo) {
    try {
      Files.createFile(markerFor(repo));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Path markerFor(RepoIdentity repo) {
    return indexRoot.resolve(repo.name() + ".indexed");
  }
}
