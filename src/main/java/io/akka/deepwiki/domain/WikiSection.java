package io.akka.deepwiki.domain;

import java.util.List;

/** SPEC-001 §2. `subsections` is null when the section has none, matching the source. */
public record WikiSection(String id, String title, List<String> pages, List<String> subsections) {}
