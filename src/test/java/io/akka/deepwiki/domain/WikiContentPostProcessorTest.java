package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.akka.deepwiki.domain.WikiContentPostProcessor.RepoUrlContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 R9-R10. Mirrors tests/backend/services/test_wiki_content.py exactly, case for case. */
class WikiContentPostProcessorTest {

  private static final RepoUrlContext GITHUB =
      new RepoUrlContext(RepoType.GITHUB, "https://github.com/AsyncFuncAI/deepwiki-open", "main");
  private static final String BASE = "https://github.com/AsyncFuncAI/deepwiki-open/blob/main";

  @Test
  void generateFileUrlGithub() {
    assertEquals(BASE + "/README.md", WikiContentPostProcessor.generateFileUrl("README.md", GITHUB));
  }

  @Test
  void generateFileUrlGitlab() {
    RepoUrlContext ctx = new RepoUrlContext(RepoType.GITLAB, "https://gitlab.com/o/r", "dev");
    assertEquals("https://gitlab.com/o/r/-/blob/dev/a/b.py", WikiContentPostProcessor.generateFileUrl("a/b.py", ctx));
  }

  @Test
  void generateFileUrlBitbucket() {
    RepoUrlContext ctx = new RepoUrlContext(RepoType.BITBUCKET, "https://bitbucket.org/o/r", "main");
    assertEquals("https://bitbucket.org/o/r/src/main/a/b.py", WikiContentPostProcessor.generateFileUrl("a/b.py", ctx));
  }

  @Test
  void generateFileUrlLocalReturnsBarePath() {
    RepoUrlContext ctx = new RepoUrlContext(RepoType.LOCAL, null, "main");
    assertEquals("a/b.py", WikiContentPostProcessor.generateFileUrl("a/b.py", ctx));
  }

  @Test
  void resolvesCitationForFileInFilePaths() {
    String out = WikiContentPostProcessor.postProcessWikiContent("text [README.md:1-27]().", List.of("README.md"), GITHUB);
    assertTrue(out.contains("[README.md:1-27](" + BASE + "/README.md#L1-L27)"));
    assertFalse(out.contains("]()"));
  }

  @Test
  void resolvesGenericCitationNotInFilePaths() {
    String path = "src/i18n.ts";
    String out = WikiContentPostProcessor.postProcessWikiContent("see [" + path + ":67-111]().", List.of("src/utils/getRepoUrl.tsx"), GITHUB);
    assertTrue(out.contains("[" + path + ":67-111](" + BASE + "/" + path + "#L67-L111)"));
    assertFalse(out.contains("]()"));
  }

  @Test
  void stripsRedundantEmptyParensAfterLink() {
    String path = "src/app/page.tsx";
    String text = "x [" + path + "](" + BASE + "/" + path + ")()";
    assertEquals("x [" + path + "](" + BASE + "/" + path + ")", WikiContentPostProcessor.postProcessWikiContent(text, List.of(), GITHUB));
  }

  @Test
  void resolvesSourcesPrefixBareFilename() {
    String full = "src/i18n.ts";
    String out = WikiContentPostProcessor.postProcessWikiContent("flow [Sources: i18n.ts:1-10]().", List.of(full), GITHUB);
    assertTrue(out.contains("Sources: [" + full + ":1-10](" + BASE + "/" + full + "#L1-L10)"));
    assertFalse(out.contains("]()"));
  }

  @Test
  void unknownBareFilenameLeftUntouched() {
    String text = "[Sources: not_exist.tsx:1-47]()";
    String out = WikiContentPostProcessor.postProcessWikiContent(text, List.of("src/app/page.tsx"), GITHUB);
    assertTrue(out.contains("[Sources: not_exist.tsx:1-47]()"));
  }

  @Test
  void rebuildsDetailsBlockWhenMissing() {
    String out = WikiContentPostProcessor.postProcessWikiContent("# Title", List.of("README.md"), GITHUB);
    assertTrue(out.startsWith("<details>"));
    assertTrue(out.contains("[README.md](" + BASE + "/README.md)"));
  }

  @Test
  void localRepoCitationsNotResolved() {
    RepoUrlContext ctx = new RepoUrlContext(RepoType.LOCAL, null, "main");
    assertTrue(WikiContentPostProcessor.postProcessWikiContent("[a/b.py:1-2]()", List.of("a/b.py"), ctx).contains("[a/b.py:1-2]()"));
  }

  @Test
  void escapesBracketsInDynamicRoutePath() {
    String path = "src/app/[owner]/[repo]/page.tsx";
    String out = WikiContentPostProcessor.postProcessWikiContent("[" + path + ":10]()", List.of(path), GITHUB);
    assertTrue(out.contains("src/app/\\[owner\\]/\\[repo\\]/page.tsx:10]("));
  }

  @Test
  void singleLineCitation() {
    String out = WikiContentPostProcessor.postProcessWikiContent("[README.md:15]()", List.of("README.md"), GITHUB);
    assertTrue(out.contains("[README.md:15](" + BASE + "/README.md#L15)"));
  }

  @Test
  void longestKnownPathWinsWhenOneIsASubstringOfAnother() {
    // "a.py" and "src/a.py" both known; the citation text ends in "src/a.py" and must
    // resolve as that whole path, not accidentally split so a shorter alternative matches.
    String out = WikiContentPostProcessor.postProcessWikiContent("[src/a.py:5]()", List.of("a.py", "src/a.py"), GITHUB);
    assertTrue(out.contains("[src/a.py:5](" + BASE + "/src/a.py#L5)"));
  }

  @Test
  void wholeFileCitationNoLines() {
    String out = WikiContentPostProcessor.postProcessWikiContent("[README.md]()", List.of("README.md"), GITHUB);
    assertTrue(out.contains("[README.md](" + BASE + "/README.md)"));
  }
}
