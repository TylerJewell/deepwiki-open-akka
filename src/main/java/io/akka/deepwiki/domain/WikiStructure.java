package io.akka.deepwiki.domain;

import java.util.List;

/**
 * SPEC-001 §2. `rootSections` is every section id never referenced as someone else's
 * subsection (R8).
 */
public record WikiStructure(
    String id, String title, String description, List<WikiPage> pages, List<WikiSection> sections, List<String> rootSections) {

  public static final String ID = "wiki";
}
