package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R15. Port of the export behaviour in api/services/wiki/io.py:146-235. */
class WikiExporterTest {

  private static final WikiPage PAGE_1 = new WikiPage("page-1", "Intro", "intro body", List.of("README.md"), "high", List.of("page-2"));
  private static final WikiPage PAGE_2 = new WikiPage("page-2", "Architecture", "arch body", List.of("a.py"), "medium", List.of());

  @Test
  void jsonExportWrapsMetadataAndPages() throws Exception {
    String json = WikiExporter.export("https://github.com/o/r", List.of(PAGE_1, PAGE_2), WikiExporter.Format.JSON, LocalDateTime.of(2026, 1, 1, 0, 0));
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("https://github.com/o/r", root.at("/metadata/repository").asText());
    assertEquals(2, root.at("/metadata/page_count").asInt());
    assertEquals("README.md", root.at("/pages/0/filePaths/0").asText());
  }

  @Test
  void markdownExportHasTocAndRelatedPagesHeaderEvenWhenUnresolved() {
    WikiPage danglingRelated = new WikiPage("page-3", "Orphan", "body", List.of(), "low", List.of("does-not-exist"));
    String md = WikiExporter.export("repo", List.of(danglingRelated), WikiExporter.Format.MARKDOWN, LocalDateTime.of(2026, 1, 1, 0, 0));
    assertTrue(md.contains("## Table of Contents"));
    assertTrue(md.contains("[Orphan](#page-3)"));
    // Header prints whenever relatedPages is non-empty, even if nothing resolves (R15).
    assertTrue(md.contains("### Related Pages"));
    assertFalse(md.contains("Related topics:"));
  }

  @Test
  void markdownExportRelatedTopicsLineWhenResolved() {
    String md = WikiExporter.export("repo", List.of(PAGE_1, PAGE_2), WikiExporter.Format.MARKDOWN, LocalDateTime.of(2026, 1, 1, 0, 0));
    assertTrue(md.contains("Related topics: [Architecture](#page-2)"));
  }

  @Test
  void unknownFormatIsNotReachableThroughTheEnum() {
    // The enum itself makes "unknown format" unrepresentable at this layer; the HTTP
    // boundary (WikiEndpoint) is what rejects an unrecognized wire string (R15).
    assertEquals(2, WikiExporter.Format.values().length);
  }
}
