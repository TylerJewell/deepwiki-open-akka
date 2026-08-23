package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 R7. Confirms the fix for question-log rows 4/6 (SPEC-001 §4.2): the file tree is
 * one path per line, never a Java/Python collection repr.
 */
class WikiPromptBuilderTest {

  @Test
  void structurePromptJoinsFileTreeOnePathPerLine() {
    String prompt =
        WikiPromptBuilder.buildStructurePrompt("acme", "widget", List.of("README.md", "src/a.py", "src/b.py"), "readme text", true, "en");
    String block = prompt.substring(prompt.indexOf("<file_tree>"), prompt.indexOf("</file_tree>"));
    assertTrue(block.contains("README.md\nsrc/a.py\nsrc/b.py"));
    assertFalse(block.contains("["));
    assertFalse(block.contains(","));
  }

  @Test
  void comprehensiveVsConciseSwitchesPageCountAndSchema() {
    String comprehensive = WikiPromptBuilder.buildStructurePrompt("a", "b", List.of("x"), "", true, "en");
    String concise = WikiPromptBuilder.buildStructurePrompt("a", "b", List.of("x"), "", false, "en");
    assertTrue(comprehensive.contains("8-12"));
    assertTrue(comprehensive.contains("<sections>"));
    assertTrue(concise.contains("4-6"));
    assertFalse(concise.contains("<sections>"));
  }

  @Test
  void pagePromptEmbedsTitleAndFileLinks() {
    String prompt = WikiPromptBuilder.buildPagePrompt("Getting Started", "- [README.md](url)", "en");
    assertTrue(prompt.contains("# Getting Started"));
    assertTrue(prompt.contains("- [README.md](url)"));
  }

  @Test
  void languageNameFallsBackToEnglish() {
    assertTrue(WikiPromptBuilder.languageName("xx").equals("English"));
    assertTrue(WikiPromptBuilder.languageName("ja").contains("Japanese"));
  }
}
