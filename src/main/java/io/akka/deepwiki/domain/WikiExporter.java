package io.akka.deepwiki.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SPEC-001 R15. Port of {@code export_wiki}/{@code _generate_json_export}/
 * {@code _generate_markdown_export} (api/services/wiki/io.py:146-235). */
public final class WikiExporter {

  private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private WikiExporter() {}

  public enum Format {
    JSON,
    MARKDOWN
  }

  public static String export(String repoUrl, List<WikiPage> pages, Format format, LocalDateTime timestamp) {
    return switch (format) {
      case JSON -> jsonExport(repoUrl, pages, timestamp);
      case MARKDOWN -> markdownExport(repoUrl, pages, timestamp);
    };
  }

  private static String jsonExport(String repoUrl, List<WikiPage> pages, LocalDateTime timestamp) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("repository", repoUrl);
    metadata.put("generated_at", timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    metadata.put("page_count", pages.size());
    Map<String, Object> export = new LinkedHashMap<>();
    export.put("metadata", metadata);
    export.put("pages", pages);
    try {
      return MAPPER.writeValueAsString(export);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String markdownExport(String repoUrl, List<WikiPage> pages, LocalDateTime timestamp) {
    StringBuilder md = new StringBuilder();
    md.append("# Wiki Documentation for ").append(repoUrl).append("\n\n");
    md.append("Generated on: ").append(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

    md.append("## Table of Contents\n\n");
    for (WikiPage page : pages) {
      md.append("- [").append(page.title()).append("](#").append(page.id()).append(")\n");
    }
    md.append("\n");

    for (WikiPage page : pages) {
      md.append("<a id='").append(page.id()).append("'></a>\n\n");
      md.append("## ").append(page.title()).append("\n\n");

      if (page.relatedPages() != null && !page.relatedPages().isEmpty()) {
        // Header prints whenever relatedPages is non-empty; the "Related topics:" line
        // only if any of those ids actually resolve to a page in this export.
        md.append("### Related Pages\n\n");
        List<String> relatedTitles =
            page.relatedPages().stream()
                .map(relatedId -> pages.stream().filter(p -> p.id().equals(relatedId)).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(p -> "[" + p.title() + "](#" + p.id() + ")")
                .toList();
        if (!relatedTitles.isEmpty()) {
          md.append("Related topics: ").append(String.join(", ", relatedTitles)).append("\n\n");
        }
      }

      md.append(page.content()).append("\n\n");
      md.append("---\n\n");
    }

    return md.toString();
  }
}
