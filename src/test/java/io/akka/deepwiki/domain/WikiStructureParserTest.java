package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** SPEC-001 R8. Mirrors tests/backend/services/test_wiki_structure.py's parsing tests. */
class WikiStructureParserTest {

  private static final String COMPREHENSIVE_XML =
      """
      <wiki_structure>
        <title>My Wiki</title>
        <description>A description</description>
        <sections>
          <section id="section-1">
            <title>Overview</title>
            <pages><page_ref>page-1</page_ref></pages>
            <subsections><section_ref>section-2</section_ref></subsections>
          </section>
          <section id="section-2">
            <title>Architecture</title>
            <pages><page_ref>page-2</page_ref></pages>
          </section>
        </sections>
        <pages>
          <page id="page-1">
            <title>Intro</title>
            <importance>high</importance>
            <relevant_files><file_path>README.md</file_path></relevant_files>
            <related_pages><related>page-2</related></related_pages>
          </page>
          <page id="page-2">
            <title>Arch</title>
            <importance>medium</importance>
            <relevant_files><file_path>src/a.py</file_path></relevant_files>
          </page>
        </pages>
      </wiki_structure>
      """;

  @Test
  void parseComprehensive() {
    WikiStructure s = WikiStructureParser.parse(COMPREHENSIVE_XML, true);
    assertEquals("My Wiki", s.title());
    assertEquals("A description", s.description());
    assertEquals(List.of("page-1", "page-2"), s.pages().stream().map(WikiPage::id).toList());
    assertEquals(List.of("README.md"), s.pages().get(0).filePaths());
    assertEquals(List.of("page-2"), s.pages().get(0).relatedPages());
    assertEquals("high", s.pages().get(0).importance());
    assertEquals(Set.of("section-1", "section-2"), s.sections().stream().map(WikiSection::id).collect(Collectors.toSet()));
    assertEquals(List.of("section-1"), s.rootSections());
  }

  @Test
  void parseConciseIgnoresSections() {
    String xml =
        "<wiki_structure><title>W</title><description>d</description><pages>"
            + "<page id=\"page-1\"><title>P</title><importance>low</importance>"
            + "<relevant_files><file_path>a.py</file_path></relevant_files></page>"
            + "</pages></wiki_structure>";
    WikiStructure s = WikiStructureParser.parse(xml, false);
    assertEquals(1, s.pages().size());
    assertEquals("low", s.pages().get(0).importance());
    assertTrue(s.sections().isEmpty());
    assertTrue(s.rootSections().isEmpty());
  }

  @Test
  void parseEscapesBareAmpersand() {
    String xml =
        "<wiki_structure><title>Frontend & Backend</title><description>d</description>"
            + "<pages><page id=\"page-1\"><title>P</title><importance>high</importance>"
            + "<relevant_files><file_path>a.py</file_path></relevant_files></page></pages></wiki_structure>";
    WikiStructure s = WikiStructureParser.parse(xml, false);
    assertEquals("Frontend & Backend", s.title());
    assertEquals(1, s.pages().size());
  }

  @Test
  void parseRegexFallbackOnMalformedXml() {
    String xml =
        "<wiki_structure>\n  <title>Broken</oops>\n  <pages><page id=\"page-1\"><title>P1</title>"
            + "<importance>high</importance>\n  <relevant_files><file_path>a.py</file_path></relevant_files>"
            + "</page></pages>\n</wiki_structure>";
    WikiStructure s = WikiStructureParser.parse(xml, false);
    assertEquals(List.of("page-1"), s.pages().stream().map(WikiPage::id).toList());
    assertEquals(List.of("a.py"), s.pages().get(0).filePaths());
  }

  @Test
  void parseNoStructureRaises() {
    assertThrows(IllegalArgumentException.class, () -> WikiStructureParser.parse("no xml here", false));
  }

  private static final String TRUNCATED_XML =
      """
      <wiki_structure>
        <title>DeepWiki-Open Wiki</title>
        <description>An AI-powered documentation generator for repositories.</description>
        <sections>
          <section id="section-1">
            <title>Overview</title>
            <pages><page_ref>page-1</page_ref></pages>
          </section>
          <section id="section-2">
            <title>Extensibility and Customization</title>
            <pages><page_ref>page-3</page_ref></pages>
          </section>
        </sections>
        <pages>
          <page id="page-1">
            <title>Project Overview</title>
            <importance>high</importance>
            <relevant_files><file_path>README.md</file_path></relevant_files>
            <related_pages><related>page-2</related></related_pages>
          </page>
          <page id="page-2">
            <title>System Architecture</title>
            <importance>high</importance>
            <relevant_files><file_path>api/main.py</file_path></relevant_files>
          </page>
          <page id="page-3">
            <title>Deployment and Infrastructure</title>
            <importance>medium</importance>
            <relevant_files>
              <file_path>docker-compose.yml</file_path>
              <file_path>Ollama-instruction.md</file_path>""";

  @Test
  void parseRecoversFromTruncatedResponse() {
    WikiStructure s = WikiStructureParser.parse(TRUNCATED_XML, true);
    assertEquals("DeepWiki-Open Wiki", s.title());
    assertTrue(s.description().contains("AI-powered"));
    assertEquals(List.of("page-1", "page-2"), s.pages().stream().map(WikiPage::id).toList());
    assertEquals(List.of("README.md"), s.pages().get(0).filePaths());
    assertEquals(Set.of("section-1", "section-2"), s.sections().stream().map(WikiSection::id).collect(Collectors.toSet()));
    assertEquals(Set.of("section-1", "section-2"), Set.copyOf(s.rootSections()));
  }

  @Test
  void parseTruncatedWithoutOpeningTagStillRaises() {
    assertThrows(IllegalArgumentException.class, () -> WikiStructureParser.parse("some prose, no xml here at all", true));
  }
}
