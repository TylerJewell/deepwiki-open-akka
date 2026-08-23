package io.akka.deepwiki.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * SPEC-001 R1-R3. Ported from {@code api/repository.py}'s {@code Repo} class: name
 * extraction, local-vs-remote detection, and clone save-path derivation.
 */
public record RepoIdentity(String reference, RepoType type, String rootPath) {

  private static final List<String> HOSTED_TYPES = List.of("github", "gitlab", "bitbucket");

  public String name() {
    if (isLocal()) {
      return basename(reference);
    }
    // Only the URL branch strips a trailing slash before splitting (api/repository.py:176).
    String trimmed = stripTrailingSlash(reference);
    String[] parts = trimmed.split("/");
    if (HOSTED_TYPES.contains(type.wire()) && parts.length >= 5) {
      String owner = parts[parts.length - 2];
      String repo = parts[parts.length - 1].replace(".git", "");
      return owner + "_" + repo;
    }
    return parts[parts.length - 1].replace(".git", "");
  }

  public boolean isLocal() {
    return !isUrl(reference);
  }

  public String savePath() {
    if (isLocal()) {
      return reference;
    }
    return rootPath + "/" + name();
  }

  private static String stripTrailingSlash(String s) {
    int end = s.length();
    while (end > 0 && s.charAt(end - 1) == '/') {
      end--;
    }
    return s.substring(0, end);
  }

  /** Matches Python's {@code os.path.basename}: text after the last '/', "" if none. */
  private static String basename(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static boolean isUrl(String path) {
    try {
      URI uri = new URI(path);
      String scheme = uri.getScheme();
      return scheme != null
          && (scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp"))
          && uri.getHost() != null
          && !uri.getHost().isEmpty();
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
