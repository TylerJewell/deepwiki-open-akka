package io.akka.deepwiki.bench;

import io.akka.deepwiki.domain.FileTreeReader;
import io.akka.deepwiki.domain.RepoIdentity;
import io.akka.deepwiki.domain.RepoType;
import io.akka.deepwiki.domain.WikiContentPostProcessor;
import io.akka.deepwiki.domain.WikiContentPostProcessor.RepoUrlContext;
import io.akka.deepwiki.domain.WikiStructure;
import io.akka.deepwiki.domain.WikiStructureParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Port-side counterpart to {@code bench/oracle_source.py} — the identical five workloads,
 * same shape, run against this rebuild's own domain package. Not a JUnit test: run
 * directly (`mvn -q test-compile exec:java -Dexec.mainClass=...` or via the compiled
 * test-classes on the classpath). {@code bench/compare.py} diffs this JSON against the
 * oracle's.
 */
public final class BenchMain {

  private static final int WINDOWS = 5;

  private interface Op {
    void run();
  }

  /** Median of WINDOWS windows -- ns/op. Mirrors bench/oracle_source.py's `timed()`. */
  private static double timed(Op op, int reps) {
    double[] samples = new double[WINDOWS];
    for (int w = 0; w < WINDOWS; w++) {
      long start = System.nanoTime();
      for (int i = 0; i < reps; i++) {
        op.run();
      }
      samples[w] = (double) (System.nanoTime() - start) / reps;
    }
    java.util.Arrays.sort(samples);
    return samples[WINDOWS / 2];
  }

  public static void main(String[] args) throws IOException {
    StringBuilder out = new StringBuilder("{\n");

    long t0 = System.nanoTime();
    List<String> names =
        List.of(
            new RepoIdentity("https://github.com/owner/repo", RepoType.GITHUB, "/root").name(),
            new RepoIdentity("https://github.com/owner/repo.git", RepoType.GITHUB, "/root").name(),
            new RepoIdentity("https://gitlab.com/group/subgroup/repo", RepoType.GITLAB, "/root").name(),
            new RepoIdentity("https://bitbucket.org/owner/repo", RepoType.BITBUCKET, "/root").name(),
            new RepoIdentity("/var/repos/project.git", RepoType.LOCAL, "/root").name());
    double repoNamesSec = (System.nanoTime() - t0) / 1e9;
    double repoNamesNs =
        timed(
            () -> {
              new RepoIdentity("https://github.com/owner/repo", RepoType.GITHUB, "/root").name();
              new RepoIdentity("https://github.com/owner/repo.git", RepoType.GITHUB, "/root").name();
              new RepoIdentity("https://gitlab.com/group/subgroup/repo", RepoType.GITLAB, "/root").name();
              new RepoIdentity("https://bitbucket.org/owner/repo", RepoType.BITBUCKET, "/root").name();
              new RepoIdentity("/var/repos/project.git", RepoType.LOCAL, "/root").name();
            },
            2000);
    out.append(workload("repo-names", String.join("|", names), repoNamesSec, repoNamesNs)).append(",\n");

    t0 = System.nanoTime();
    Path tmp = Files.createTempDirectory("deepwiki-bench");
    for (String rel : List.of("README.md", "src/a.py", "src/b.py", "vendor/c.py", "app.min.js", "app.js", ".env", "__pycache__/junk.pyc", ".hidden")) {
      Path p = tmp.resolve(rel);
      Files.createDirectories(p.getParent());
      Files.writeString(p, "x", StandardCharsets.UTF_8);
    }
    List<String> files = FileTreeReader.iterateFiles(tmp, List.of("vendor"), null, null, null).stream().sorted().toList();
    double fileTreeSec = (System.nanoTime() - t0) / 1e9;
    double fileTreeNs = timed(() -> FileTreeReader.iterateFiles(tmp, List.of("vendor"), null, null, null), 500);
    out.append(workload("file-tree", String.join("|", files), fileTreeSec, fileTreeNs)).append(",\n");

    t0 = System.nanoTime();
    String comprehensiveXml =
        "<wiki_structure><title>My Wiki</title><description>A description</description>"
            + "<sections><section id=\"section-1\"><title>Overview</title><pages><page_ref>page-1</page_ref></pages>"
            + "<subsections><section_ref>section-2</section_ref></subsections></section>"
            + "<section id=\"section-2\"><title>Architecture</title><pages><page_ref>page-2</page_ref></pages></section></sections>"
            + "<pages><page id=\"page-1\"><title>Intro</title><importance>high</importance>"
            + "<relevant_files><file_path>README.md</file_path></relevant_files>"
            + "<related_pages><related>page-2</related></related_pages></page>"
            + "<page id=\"page-2\"><title>Arch</title><importance>medium</importance>"
            + "<relevant_files><file_path>src/a.py</file_path></relevant_files></page></pages></wiki_structure>";
    String truncatedXml =
        "<wiki_structure><title>DeepWiki-Open Wiki</title>"
            + "<description>An AI-powered documentation generator for repositories.</description>"
            + "<sections><section id=\"section-1\"><title>Overview</title><pages><page_ref>page-1</page_ref></pages></section>"
            + "<section id=\"section-2\"><title>Extensibility and Customization</title><pages><page_ref>page-3</page_ref></pages></section></sections>"
            + "<pages><page id=\"page-1\"><title>Project Overview</title><importance>high</importance>"
            + "<relevant_files><file_path>README.md</file_path></relevant_files>"
            + "<related_pages><related>page-2</related></related_pages></page>"
            + "<page id=\"page-2\"><title>System Architecture</title><importance>high</importance>"
            + "<relevant_files><file_path>api/main.py</file_path></relevant_files></page>"
            + "<page id=\"page-3\"><title>Deployment and Infrastructure</title><importance>medium</importance>"
            + "<relevant_files><file_path>docker-compose.yml</file_path><file_path>Ollama-instruction.md</file_path>";
    String malformedAmpersand =
        "<wiki_structure><title>Frontend & Backend</title><description>d</description>"
            + "<pages><page id=\"page-1\"><title>P</title><importance>high</importance>"
            + "<relevant_files><file_path>a.py</file_path></relevant_files></page></pages></wiki_structure>";
    List<WikiStructure> parsed =
        List.of(
            WikiStructureParser.parse(comprehensiveXml, true),
            WikiStructureParser.parse(truncatedXml, true),
            WikiStructureParser.parse(malformedAmpersand, false));
    String structureAnswer =
        parsed.stream()
            .map(
                s ->
                    s.title()
                        + "|"
                        + s.description()
                        + "|"
                        + pyList(s.pages().stream().map(p -> p.id()).toList())
                        + "|"
                        + pyListOfLists(s.pages().stream().map(p -> p.filePaths()).toList()))
            .collect(Collectors.joining("|"));
    double structureSec = (System.nanoTime() - t0) / 1e9;
    double structureNs =
        timed(
            () -> {
              WikiStructureParser.parse(comprehensiveXml, true);
              WikiStructureParser.parse(truncatedXml, true);
              WikiStructureParser.parse(malformedAmpersand, false);
            },
            500);
    out.append(workload("structure-parse", structureAnswer, structureSec, structureNs)).append(",\n");

    t0 = System.nanoTime();
    RepoUrlContext ctx = new RepoUrlContext(RepoType.GITHUB, "https://github.com/AsyncFuncAI/deepwiki-open", "main");
    List<String> filePaths = List.of("README.md", "src/utils/getRepoUrl.tsx", "src/i18n.ts", "src/app/[owner]/[repo]/page.tsx");
    List<String> citations =
        List.of(
            "text [README.md:1-27]().",
            "see [src/i18n.ts:67-111]().",
            "flow [Sources: i18n.ts:1-10]().",
            "[Sources: not_exist.tsx:1-47]()",
            "[src/app/[owner]/[repo]/page.tsx:10]()");
    String citationAnswer =
        citations.stream().map(c -> WikiContentPostProcessor.postProcessWikiContent(c, filePaths, ctx)).collect(Collectors.joining("|"));
    double citationsSec = (System.nanoTime() - t0) / 1e9;
    double citationsNs =
        timed(() -> citations.forEach(c -> WikiContentPostProcessor.postProcessWikiContent(c, filePaths, ctx)), 500);
    out.append(workload("citations", citationAnswer, citationsSec, citationsNs)).append("\n");

    out.append("}");
    System.out.println(out);
  }

  private static String pyList(List<String> values) {
    return "[" + values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", ")) + "]";
  }

  private static String pyListOfLists(List<List<String>> values) {
    return "[" + values.stream().map(BenchMain::pyList).collect(Collectors.joining(", ")) + "]";
  }

  private static String workload(String name, String answer, double seconds, double nsPerOp) {
    CRC32 crc = new CRC32();
    crc.update(answer.getBytes(StandardCharsets.UTF_8));
    return "  \"" + name + "\": {\"checksum\": " + crc.getValue() + ", \"seconds\": " + seconds + ", \"ns_per_op\": " + nsPerOp + "}";
  }
}
