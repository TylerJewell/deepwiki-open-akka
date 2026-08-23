package io.akka.deepwiki.domain;

/** SPEC-001 R11. Exactly one of created/joined/fromCache is true, unless the task is brand new. */
public record WikiTaskSubmitResult(String taskId, TaskStatus status, boolean created, boolean joined, boolean fromCache) {

  public static WikiTaskSubmitResult created(String taskId, TaskStatus status) {
    return new WikiTaskSubmitResult(taskId, status, true, false, false);
  }

  public static WikiTaskSubmitResult joined(String taskId, TaskStatus status) {
    return new WikiTaskSubmitResult(taskId, status, false, true, false);
  }

  public static WikiTaskSubmitResult fromCache(String taskId) {
    return new WikiTaskSubmitResult(taskId, TaskStatus.COMPLETED, false, false, true);
  }
}
