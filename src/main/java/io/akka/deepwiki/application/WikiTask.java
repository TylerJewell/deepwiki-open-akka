package io.akka.deepwiki.application;

import io.akka.deepwiki.domain.TaskStatus;
import io.akka.deepwiki.domain.WikiStructure;
import io.akka.deepwiki.domain.WikiTaskRequest;
import io.akka.deepwiki.domain.WikiTaskStatus;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory runtime state for one repo's generation task — port of the source's
 * {@code WikiTask} (api/services/wiki/tasks.py:73-137). Mutable by design: the source's own
 * task object is updated in place as the state machine advances, and nothing durable is
 * expected to survive a process restart (SPEC-001 §4.4).
 */
public final class WikiTask {

  private final WikiTaskRequest request;
  private final long submittedAt;
  private final AtomicReference<TaskStatus> status = new AtomicReference<>(TaskStatus.PENDING);
  private final AtomicInteger pagesDone = new AtomicInteger(0);
  private final CopyOnWriteArrayList<String> currentPageIds = new CopyOnWriteArrayList<>();
  private final AtomicReference<WikiStructure> wikiStructure = new AtomicReference<>();
  private final AtomicReference<String> defaultBranch = new AtomicReference<>("main");
  private final AtomicReference<String> error = new AtomicReference<>();

  public WikiTask(WikiTaskRequest request, long submittedAt) {
    this.request = request;
    this.submittedAt = submittedAt;
  }

  public WikiTaskRequest request() {
    return request;
  }

  public String repoKey() {
    return request.repoKey();
  }

  public TaskStatus status() {
    return status.get();
  }

  public void setStatus(TaskStatus s) {
    status.set(s);
  }

  public void setError(String message) {
    error.set(message);
  }

  public String defaultBranch() {
    return defaultBranch.get();
  }

  public void setDefaultBranch(String branch) {
    defaultBranch.set(branch);
  }

  public WikiStructure wikiStructure() {
    return wikiStructure.get();
  }

  public void setWikiStructure(WikiStructure structure) {
    wikiStructure.set(structure);
  }

  public void addCurrentPageId(String id) {
    currentPageIds.add(id);
  }

  public void removeCurrentPageId(String id) {
    currentPageIds.remove(id);
  }

  public void incrementPagesDone() {
    pagesDone.incrementAndGet();
  }

  public int pagesTotal() {
    WikiStructure s = wikiStructure.get();
    return s == null ? 0 : s.pages().size();
  }

  public WikiTaskStatus toStatus() {
    return new WikiTaskStatus(
        repoKey(),
        request.owner(),
        request.repo(),
        request.type(),
        request.language(),
        status.get(),
        pagesDone.get(),
        pagesTotal(),
        List.copyOf(currentPageIds),
        wikiStructure.get(),
        error.get(),
        submittedAt);
  }
}
