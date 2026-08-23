package io.akka.deepwiki.application;

import io.akka.deepwiki.domain.RepoIdentity;

/**
 * SPEC-001 §4.1: the embedding/RAG-index boundary this port does not reimplement. The
 * wiki task state machine only ever asks "does an index already exist for this repo?" —
 * building or querying that index is a separate capability (out of scope, SPEC-001 §1).
 */
public interface RepositoryIndexer {

  boolean indexExists(RepoIdentity repo);

  void buildIndex(RepoIdentity repo);
}
