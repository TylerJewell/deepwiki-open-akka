package io.akka.deepwiki.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepoType {
  LOCAL,
  GITHUB,
  GITLAB,
  BITBUCKET;

  /** Wire form matches the source's own lowercase string enum (api/schemas/base.py). */
  @JsonValue
  public String wire() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static RepoType fromWire(String value) {
    return RepoType.valueOf(value.toUpperCase());
  }
}
