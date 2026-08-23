package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SPEC-001 R4-R6. Mirrors tests/backend/services/test_wiki_structure.py's file-tree tests. */
class FileTreeReaderTest {

  @Test
  void readRepoFileTree(@TempDir Path tmp) throws IOException {
    Files.writeString(tmp.resolve("README.md"), "hello readme", StandardCharsets.UTF_8);
    Files.createDirectory(tmp.resolve("src"));
    Files.writeString(tmp.resolve("src/a.py"), "x", StandardCharsets.UTF_8);
    Files.writeString(tmp.resolve(".hidden"), "h", StandardCharsets.UTF_8);
    Files.createDirectory(tmp.resolve("__pycache__"));
    Files.writeString(tmp.resolve("__pycache__/junk.pyc"), "j", StandardCharsets.UTF_8);

    FileTreeReader.FileTree tree = FileTreeReader.readRepoFileTree(tmp, null, null, null, null);
    assertTrue(tree.files().contains("README.md"));
    assertTrue(tree.files().contains("src/a.py"));
    assertFalse(tree.files().contains(".hidden"));
    assertTrue(tree.files().stream().noneMatch(f -> f.contains("__pycache__")));
    assertEquals("hello readme", tree.readme());
  }

  @Test
  void detectDefaultBranchNonGitDir(@TempDir Path tmp) {
    assertEquals("main", FileTreeReader.detectDefaultBranch(tmp));
  }

  @Test
  void excludedDirMatchesAnywhereInPath(@TempDir Path tmp) throws IOException {
    Files.createDirectories(tmp.resolve("src"));
    Files.createDirectories(tmp.resolve("vendor"));
    Files.writeString(tmp.resolve("src/a.py"), "x", StandardCharsets.UTF_8);
    Files.writeString(tmp.resolve("vendor/b.py"), "y", StandardCharsets.UTF_8);

    List<String> files = FileTreeReader.iterateFiles(tmp, List.of("vendor"), null, null, null);
    assertTrue(files.contains("src/a.py"));
    assertFalse(files.contains("vendor/b.py"));
  }

  @Test
  void inclusionModeKeepsOnlyListedDir(@TempDir Path tmp) throws IOException {
    Files.createDirectories(tmp.resolve("src"));
    Files.createDirectories(tmp.resolve("vendor"));
    Files.writeString(tmp.resolve("src/a.py"), "x", StandardCharsets.UTF_8);
    Files.writeString(tmp.resolve("vendor/b.py"), "y", StandardCharsets.UTF_8);

    List<String> files = FileTreeReader.iterateFiles(tmp, null, null, List.of("src"), null);
    assertEquals(List.of("src/a.py"), files);
  }

  @Test
  void globShapedDefaultExclusionsAreLiteralNotGlob_row7(@TempDir Path tmp) throws IOException {
    Files.writeString(tmp.resolve("app.js"), "x", StandardCharsets.UTF_8);
    Files.writeString(tmp.resolve("app.min.js"), "y", StandardCharsets.UTF_8);
    Files.writeString(tmp.resolve(".env"), "SECRET=1", StandardCharsets.UTF_8);

    List<String> files = FileTreeReader.iterateFiles(tmp, null, null, null, null);
    // question-log row 7: "*.min.js" in the default excluded_files list never matches by
    // glob, only by exact filename equality -- so app.min.js is NOT excluded (this is
    // current source behaviour, carried through per SPEC-001 §4.3, not fixed here).
    assertTrue(files.contains("app.js"));
    assertTrue(files.contains("app.min.js"));
    // .env has no extension in the code/doc extension list, so it's dropped by the
    // extension filter regardless of the excluded_files check.
    assertFalse(files.contains(".env"));
  }
}
