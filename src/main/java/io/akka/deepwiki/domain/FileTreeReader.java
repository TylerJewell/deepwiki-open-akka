package io.akka.deepwiki.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SPEC-001 R4-R6. Port of {@code iterate_files}/{@code _should_process_file}
 * (api/config.py:420-516) and {@code read_repo_file_tree}/{@code detect_default_branch}
 * (api/services/wiki/structure.py).
 */
public final class FileTreeReader {

  private FileTreeReader() {}

  public record FileTree(List<String> files, String readme) {}

  /** SPEC-001 R4: walk {@code rootDir}, restrict to code/doc extensions, then filter. */
  public static List<String> iterateFiles(
      Path rootDir,
      List<String> excludedDirs,
      List<String> excludedFiles,
      List<String> includedDirs,
      List<String> includedFiles) {
    boolean useInclusion = !isEmpty(includedDirs) || !isEmpty(includedFiles);
    Set<String> incDirs = useInclusion ? cleanSet(includedDirs) : Set.of();
    Set<String> incFiles = useInclusion ? new HashSet<>(nullToEmpty(includedFiles)) : Set.of();
    Set<String> excDirs = useInclusion ? Set.of() : union(FileFilterConfig.DEFAULT_EXCLUDED_DIRS, cleanSet(excludedDirs));
    Set<String> excFiles = useInclusion ? Set.of() : union(FileFilterConfig.DEFAULT_EXCLUDED_FILES, new HashSet<>(nullToEmpty(excludedFiles)));

    Set<String> extensions =
        Stream.concat(FileFilterConfig.CODE_EXTENSIONS.stream(), FileFilterConfig.DOC_EXTENSIONS.stream())
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    List<String> results = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(rootDir)) {
      List<Path> files = walk.filter(Files::isRegularFile).toList();
      for (Path p : files) {
        String suffix = extensionOf(p.getFileName().toString());
        if (!extensions.contains(suffix)) {
          continue;
        }
        if (shouldProcessFile(p, useInclusion, incDirs, incFiles, excDirs, excFiles)) {
          results.add(relativeSlashPath(rootDir, p));
        }
      }
    } catch (IOException e) {
      throw new UncheckedFileTreeException(e);
    }
    return results;
  }

  /** SPEC-001 R4-R5: files list plus the shortest-path README's content, "" if none/unreadable. */
  public static FileTree readRepoFileTree(
      Path rootDir,
      List<String> includedFiles,
      List<String> includedDirs,
      List<String> excludedFiles,
      List<String> excludedDirs) {
    List<String> files = iterateFiles(rootDir, excludedDirs, excludedFiles, includedDirs, includedFiles);
    String readme = "";
    List<String> byLength = files.stream().sorted(Comparator.comparingInt(String::length)).toList();
    for (String relative : byLength) {
      String withoutExt = stripExtension(relative);
      if (withoutExt.toLowerCase(Locale.ROOT).endsWith("readme")) {
        try {
          readme = Files.readString(rootDir.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
          readme = "";
        }
        break;
      }
    }
    return new FileTree(files, readme);
  }

  /** SPEC-001 R6: checked-out branch of a git repo, or "main" if not one / no git binary. */
  public static String detectDefaultBranch(Path rootDir) {
    try {
      ProcessBuilder pb = new ProcessBuilder("git", "-C", rootDir.toString(), "rev-parse", "--abbrev-ref", "HEAD");
      pb.redirectErrorStream(false);
      Process process = pb.start();
      String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      int exit = process.waitFor();
      if (exit != 0 || stdout.isEmpty()) {
        return "main";
      }
      return stdout;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return "main";
    }
  }

  private static boolean shouldProcessFile(
      Path filePath,
      boolean useInclusion,
      Set<String> includedDirs,
      Set<String> includedFiles,
      Set<String> excludedDirs,
      Set<String> excludedFiles) {
    Path absolute = filePath.toAbsolutePath().normalize();
    Set<String> pathParts = new HashSet<>();
    for (Path part : absolute) {
      pathParts.add(part.toString());
    }
    String fileName = absolute.getFileName().toString();

    if (useInclusion) {
      boolean isIncluded = false;
      if (!includedDirs.isEmpty()) {
        for (String included : includedDirs) {
          if (pathParts.contains(included)) {
            isIncluded = true;
            break;
          }
        }
      }
      if (!isIncluded && !includedFiles.isEmpty()) {
        for (String includedFile : includedFiles) {
          if (fileName.equals(includedFile) || fileName.endsWith(includedFile)) {
            isIncluded = true;
            break;
          }
        }
      }
      if (includedDirs.isEmpty() && includedFiles.isEmpty()) {
        isIncluded = true;
      }
      return isIncluded;
    }

    boolean isExcluded = false;
    for (String excluded : excludedDirs) {
      if (pathParts.contains(excluded)) {
        isExcluded = true;
        break;
      }
    }
    if (!isExcluded) {
      for (String excludedFile : excludedFiles) {
        if (fileName.equals(excludedFile)) {
          isExcluded = true;
          break;
        }
      }
    }
    return !isExcluded;
  }

  private static String relativeSlashPath(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  private static String extensionOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
  }

  private static String stripExtension(String relativePath) {
    int slash = relativePath.lastIndexOf('/');
    String name = slash < 0 ? relativePath : relativePath.substring(slash + 1);
    int dot = name.lastIndexOf('.');
    String base = dot < 0 ? name : name.substring(0, dot);
    return slash < 0 ? base : relativePath.substring(0, slash + 1) + base;
  }

  private static Set<String> cleanSet(List<String> values) {
    Set<String> out = new HashSet<>();
    for (String v : nullToEmpty(values)) {
      String cleaned = v.startsWith("./") ? v.substring(2) : v;
      while (cleaned.endsWith("/")) {
        cleaned = cleaned.substring(0, cleaned.length() - 1);
      }
      out.add(cleaned);
    }
    return out;
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    Set<String> out = new HashSet<>(a);
    out.addAll(b);
    return out;
  }

  private static List<String> nullToEmpty(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static boolean isEmpty(List<String> values) {
    return values == null || values.isEmpty();
  }

  public static final class UncheckedFileTreeException extends RuntimeException {
    public UncheckedFileTreeException(IOException cause) {
      super(cause);
    }
  }
}
