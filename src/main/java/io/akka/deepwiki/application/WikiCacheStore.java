package io.akka.deepwiki.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.deepwiki.domain.RepoType;
import io.akka.deepwiki.domain.TaskStatus;
import io.akka.deepwiki.domain.WikiCacheData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SPEC-001 R14. Port of {@code get_wiki_cache_path}/{@code wiki_cache_exists}/
 * {@code read_wiki_cache}/{@code save_wiki_cache}/{@code delete_wiki_cache}/
 * {@code list_wiki_cache} (api/services/wiki/io.py:1-144).
 */
public final class WikiCacheStore {

  private static final String PREFIX = "deepwiki_cache_";
  private final Path cacheDir;
  private final ObjectMapper mapper = new ObjectMapper();

  public WikiCacheStore(Path cacheDir) {
    this.cacheDir = cacheDir;
    try {
      Files.createDirectories(cacheDir);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create wiki cache dir " + cacheDir, e);
    }
  }

  public Path cachePath(String owner, String repo, RepoType repoType, String language) {
    String filename = PREFIX + repoType.wire() + "_" + owner + "_" + repo + "_" + language + ".json";
    return cacheDir.resolve(filename);
  }

  public boolean exists(String owner, String repo, RepoType repoType, String language) {
    return Files.exists(cachePath(owner, repo, repoType, language));
  }

  public WikiCacheData read(String owner, String repo, RepoType repoType, String language) {
    Path path = cachePath(owner, repo, repoType, language);
    if (!Files.exists(path)) {
      return null;
    }
    try {
      return mapper.readValue(path.toFile(), WikiCacheData.class);
    } catch (IOException e) {
      return null;
    }
  }

  public boolean save(String owner, String repo, RepoType repoType, String language, WikiCacheData data) {
    Path path = cachePath(owner, repo, repoType, language);
    try {
      mapper.writeValue(path.toFile(), data);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public boolean delete(String owner, String repo, RepoType repoType, String language) {
    Path path = cachePath(owner, repo, repoType, language);
    if (!Files.exists(path)) {
      return false;
    }
    try {
      Files.delete(path);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public record CacheEntry(
      String id, String owner, String repo, RepoType repoType, String language, long submittedAt, TaskStatus status) {}

  public List<CacheEntry> list() {
    List<CacheEntry> entries = new ArrayList<>();
    if (!Files.isDirectory(cacheDir)) {
      return entries;
    }
    try (var stream = Files.list(cacheDir)) {
      for (Path path : stream.toList()) {
        String filename = path.getFileName().toString();
        if (!filename.startsWith(PREFIX) || !filename.endsWith(".json")) {
          continue;
        }
        String stem = filename.substring(PREFIX.length(), filename.length() - ".json".length());
        String[] parts = stem.split("_");
        if (parts.length < 3) {
          continue;
        }
        RepoType repoType;
        try {
          repoType = RepoType.fromWire(parts[0]);
        } catch (IllegalArgumentException e) {
          continue;
        }
        String owner = parts[1];
        String language = parts[parts.length - 1];
        String repo = String.join("_", List.of(parts).subList(2, parts.length - 1));
        long submittedAt = Files.getLastModifiedTime(path).toMillis();
        entries.add(new CacheEntry(filename, owner, repo, repoType, language, submittedAt, TaskStatus.COMPLETED));
      }
    } catch (IOException e) {
      return List.of();
    }
    return entries;
  }

  public record ProcessedProject(String id, String owner, String repo, String name, RepoType repoType, long submittedAt, String language) {}

  public List<ProcessedProject> listProcessedProjects() {
    return list().stream()
        .map(e -> new ProcessedProject(e.id(), e.owner(), e.repo(), e.owner() + "/" + e.repo(), e.repoType(), e.submittedAt(), e.language()))
        .sorted(Comparator.comparingLong(ProcessedProject::submittedAt).reversed())
        .toList();
  }
}
