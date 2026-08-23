package io.akka.deepwiki.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.akka.deepwiki.domain.RepoInfo;
import io.akka.deepwiki.domain.RepoType;
import io.akka.deepwiki.domain.WikiCacheData;
import io.akka.deepwiki.domain.WikiPage;
import io.akka.deepwiki.domain.WikiStructure;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SPEC-001 R14. Port of api/services/wiki/io.py's cache path/exists/read/save/delete/list. */
class WikiCacheStoreTest {

  private static WikiCacheData sampleData() {
    WikiStructure structure = new WikiStructure("wiki", "T", "d", List.of(), List.of(), List.of());
    WikiPage page = new WikiPage("page-1", "P", "body", List.of("a.py"), "high", List.of());
    return new WikiCacheData(structure, Map.of("page-1", page), new RepoInfo("acme", "widget", RepoType.GITHUB, "https://github.com/acme/widget"), "google", "gemini");
  }

  @Test
  void saveThenReadRoundTrips(@TempDir Path tmp) {
    WikiCacheStore store = new WikiCacheStore(tmp);
    assertFalse(store.exists("acme", "widget", RepoType.GITHUB, "en"));

    assertTrue(store.save("acme", "widget", RepoType.GITHUB, "en", sampleData()));
    assertTrue(store.exists("acme", "widget", RepoType.GITHUB, "en"));

    WikiCacheData read = store.read("acme", "widget", RepoType.GITHUB, "en");
    assertEquals("T", read.wikiStructure().title());
    assertEquals("body", read.generatedPages().get("page-1").content());
  }

  @Test
  void readMissingReturnsNull(@TempDir Path tmp) {
    WikiCacheStore store = new WikiCacheStore(tmp);
    assertNull(store.read("nobody", "nothing", RepoType.GITHUB, "en"));
  }

  @Test
  void deleteRemovesTheFile(@TempDir Path tmp) {
    WikiCacheStore store = new WikiCacheStore(tmp);
    store.save("acme", "widget", RepoType.GITHUB, "en", sampleData());
    assertTrue(store.delete("acme", "widget", RepoType.GITHUB, "en"));
    assertFalse(store.exists("acme", "widget", RepoType.GITHUB, "en"));
    assertFalse(store.delete("acme", "widget", RepoType.GITHUB, "en"));
  }

  @Test
  void listParsesFilenameBackIntoOwnerRepoLanguage(@TempDir Path tmp) {
    WikiCacheStore store = new WikiCacheStore(tmp);
    store.save("acme", "widget_two", RepoType.GITHUB, "en", sampleData());
    List<WikiCacheStore.CacheEntry> entries = store.list();
    assertEquals(1, entries.size());
    assertEquals("acme", entries.get(0).owner());
    assertEquals("widget_two", entries.get(0).repo());
    assertEquals("en", entries.get(0).language());
    assertEquals(RepoType.GITHUB, entries.get(0).repoType());
  }
}
