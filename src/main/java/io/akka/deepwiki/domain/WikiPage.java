package io.akka.deepwiki.domain;

import java.util.List;

/** SPEC-001 §2. `importance` is normalized to high/medium/low, default medium. */
public record WikiPage(
    String id, String title, String content, List<String> filePaths, String importance, List<String> relatedPages) {

  public WikiPage withContent(String newContent) {
    return new WikiPage(id, title, newContent, filePaths, importance, relatedPages);
  }
}
