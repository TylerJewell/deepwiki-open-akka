package io.akka.deepwiki.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
  PENDING,
  INDEXING,
  DETERMINING_STRUCTURE,
  GENERATING,
  COMPLETED,
  FAILED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED;
  }

  /** Wire form matches the source's own lowercase string enum (api/schemas/repo.py). */
  @JsonValue
  public String wire() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static TaskStatus fromWire(String value) {
    return TaskStatus.valueOf(value.toUpperCase());
  }
}
