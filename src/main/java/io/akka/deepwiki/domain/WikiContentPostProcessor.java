package io.akka.deepwiki.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPEC-001 R9-R10. Port of {@code post_process_wiki_content}/{@code generate_file_url}
 * (api/services/wiki/content.py). Pure functions.
 */
public final class WikiContentPostProcessor {

  public record RepoUrlContext(RepoType type, String repoUrl, String defaultBranch) {}

  private static final Pattern DETAILS_RE =
      Pattern.compile(
          "<details>\\s*<summary>\\s*Relevant source files\\s*</summary>[\\s\\S]*?</details>", Pattern.CASE_INSENSITIVE);
  private static final Pattern GENERIC_RE = Pattern.compile("\\[([^\\[\\]\\s()]+?\\.[A-Za-z0-9]+)(?::(\\d+)(?:-(\\d+))?)?\\]\\(\\)");
  private static final Pattern PREFIXED_RE =
      Pattern.compile("\\[(Sources?|Source):\\s*([^\\[\\]\\s():]+?)(?::(\\d+)(?:-(\\d+))?)?\\]\\(\\)", Pattern.CASE_INSENSITIVE);
  private static final Pattern STRAY_PARENS_RE = Pattern.compile("(\\]\\([^)\\s]+\\))\\(\\)");

  private WikiContentPostProcessor() {}

  public static String generateFileUrl(String filePath, RepoUrlContext ctx) {
    if (ctx.type() == RepoType.LOCAL || ctx.repoUrl() == null) {
      return filePath;
    }
    return switch (ctx.type()) {
      case GITHUB -> ctx.repoUrl() + "/blob/" + ctx.defaultBranch() + "/" + filePath;
      case GITLAB -> ctx.repoUrl() + "/-/blob/" + ctx.defaultBranch() + "/" + filePath;
      case BITBUCKET -> ctx.repoUrl() + "/src/" + ctx.defaultBranch() + "/" + filePath;
      case LOCAL -> filePath;
    };
  }

  public static String postProcessWikiContent(String content, List<String> filePaths, RepoUrlContext ctx) {
    String processed = content;

    if (!filePaths.isEmpty()) {
      String links =
          filePaths.stream().map(p -> "- [" + escapeLabel(p) + "](" + generateFileUrl(p, ctx) + ")").reduce((a, b) -> a + "\n" + b).orElse("");
      String detailsBlock =
          "<details>\n<summary>Relevant source files</summary>\n\n"
              + "The following files were used as context for generating this wiki page:\n\n"
              + links
              + "\n</details>";
      Matcher detailsMatch = DETAILS_RE.matcher(processed);
      if (detailsMatch.find()) {
        processed = detailsMatch.replaceAll(Matcher.quoteReplacement(detailsBlock));
      } else {
        processed = detailsBlock + "\n\n" + processed;
      }
    }

    if (!filePaths.isEmpty()) {
      List<String> byLengthDesc = filePaths.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
      String alternation = byLengthDesc.stream().map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElse("");
      Pattern citationRe = Pattern.compile("\\[(" + alternation + ")(?::(\\d+)(?:-(\\d+))?)?\\]\\(\\)");
      processed = replaceKnownCitations(processed, citationRe, ctx);
    }

    processed = replaceGenericCitations(processed, ctx);

    if (!filePaths.isEmpty()) {
      Map<String, String> byBasename = new HashMap<>();
      for (String p : filePaths) {
        String base = p.contains("/") ? p.substring(p.lastIndexOf('/') + 1) : p;
        byBasename.putIfAbsent(base, p);
      }
      processed = replacePrefixedCitations(processed, byBasename, ctx);
    }

    processed = STRAY_PARENS_RE.matcher(processed).replaceAll("$1");
    return processed;
  }

  private static String replaceKnownCitations(String text, Pattern citationRe, RepoUrlContext ctx) {
    Matcher m = citationRe.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String link = citationLink(m.group(1), m.group(2), m.group(3), ctx);
      m.appendReplacement(out, Matcher.quoteReplacement(link != null ? link : m.group()));
    }
    m.appendTail(out);
    return out.toString();
  }

  private static String replaceGenericCitations(String text, RepoUrlContext ctx) {
    Matcher m = GENERIC_RE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String link = citationLink(m.group(1), m.group(2), m.group(3), ctx);
      m.appendReplacement(out, Matcher.quoteReplacement(link != null ? link : m.group()));
    }
    m.appendTail(out);
    return out.toString();
  }

  private static String replacePrefixedCitations(String text, Map<String, String> byBasename, RepoUrlContext ctx) {
    Matcher m = PREFIXED_RE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String prefix = m.group(1);
      String token = m.group(2);
      String start = m.group(3);
      String end = m.group(4);
      String fullPath = token.contains("/") ? token : byBasename.get(token);
      String replacement;
      if (fullPath == null) {
        replacement = m.group();
      } else {
        String link = citationLink(fullPath, start, end, ctx);
        replacement = link == null ? m.group() : prefix + ": " + link;
      }
      m.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(out);
    return out.toString();
  }

  private static String citationLink(String path, String start, String end, RepoUrlContext ctx) {
    String url = generateFileUrl(path, ctx);
    if (url.equals(path)) {
      return null;
    }
    String linePart = start == null ? "" : (end != null ? ":" + start + "-" + end : ":" + start);
    String anchor = lineAnchor(ctx.type(), start, end);
    return "[" + escapeLabel(path) + linePart + "](" + url + anchor + ")";
  }

  private static String lineAnchor(RepoType type, String start, String end) {
    if (start == null) {
      return "";
    }
    return switch (type) {
      case GITHUB -> end != null ? "#L" + start + "-L" + end : "#L" + start;
      case GITLAB -> end != null ? "#L" + start + "-" + end : "#L" + start;
      case BITBUCKET -> end != null ? "#lines-" + start + ":" + end : "#lines-" + start;
      case LOCAL -> "";
    };
  }

  private static String escapeLabel(String s) {
    StringBuilder out = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c == '[' || c == ']') {
        out.append('\\');
      }
      out.append(c);
    }
    return out.toString();
  }
}
