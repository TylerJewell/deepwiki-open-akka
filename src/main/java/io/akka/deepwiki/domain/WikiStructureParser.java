package io.akka.deepwiki.domain;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * SPEC-001 R8. Port of {@code parse_wiki_structure} and its helpers
 * (api/services/wiki/structure.py:65-250): strict-XML-then-regex-fallback parsing of an
 * LLM's wiki-structure response, including recovery from a truncated response.
 */
public final class WikiStructureParser {

  private static final Pattern LEADING_FENCE = Pattern.compile("^```(?:xml)?\\s*", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRAILING_FENCE = Pattern.compile("```\\s*$");
  private static final Pattern STRUCTURE_BLOCK = Pattern.compile("<wiki_structure>[\\s\\S]*?</wiki_structure>");
  private static final Pattern STRUCTURE_OPEN_ONLY = Pattern.compile("<wiki_structure>[\\s\\S]*");
  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
  private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!(?:amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)");

  private static final Pattern PAGE_BLOCK = Pattern.compile("<page\\b[\\s\\S]*?</page>");
  private static final Pattern PAGE_ID = Pattern.compile("<page\\s+id=\"([^\"]+)\"");
  private static final Pattern TITLE = Pattern.compile("<title>([\\s\\S]*?)</title>");
  private static final Pattern DESCRIPTION = Pattern.compile("<description>([\\s\\S]*?)</description>");
  private static final Pattern IMPORTANCE = Pattern.compile("<importance>([\\s\\S]*?)</importance>");
  private static final Pattern FILE_PATH = Pattern.compile("<file_path>([\\s\\S]*?)</file_path>");
  private static final Pattern RELATED = Pattern.compile("<related>([\\s\\S]*?)</related>");

  private static final Pattern SECTION_BLOCK = Pattern.compile("<section\\b[\\s\\S]*?</section>");
  private static final Pattern SECTION_ID = Pattern.compile("<section\\s+id=\"([^\"]+)\"");
  private static final Pattern PAGE_REF = Pattern.compile("<page_ref>([\\s\\S]*?)</page_ref>");
  private static final Pattern SECTION_REF = Pattern.compile("<section_ref>([\\s\\S]*?)</section_ref>");

  private WikiStructureParser() {}

  public static WikiStructure parse(String text, boolean comprehensive) {
    String trimmed = TRAILING_FENCE.matcher(LEADING_FENCE.matcher(text.strip()).replaceFirst("")).replaceFirst("");

    Matcher block = STRUCTURE_BLOCK.matcher(trimmed);
    String xmlText;
    if (block.find()) {
      xmlText = block.group();
    } else {
      Matcher openOnly = STRUCTURE_OPEN_ONLY.matcher(trimmed);
      if (!openOnly.find()) {
        throw new IllegalArgumentException("No valid <wiki_structure> XML found in response");
      }
      xmlText = openOnly.group() + "\n</wiki_structure>";
    }

    xmlText = CONTROL_CHARS.matcher(xmlText).replaceAll("");
    xmlText = BARE_AMPERSAND.matcher(xmlText).replaceAll("&amp;");

    Element root = tryStrictParse(xmlText);

    String title;
    String description;
    List<WikiPage> pages;
    if (root != null) {
      title = firstChildText(root, "title");
      description = firstChildText(root, "description");
      pages = pagesFromElements(descendants(root, "page"));
    } else {
      title = firstGroup(TITLE, xmlText);
      description = firstGroup(DESCRIPTION, xmlText);
      pages = List.of();
    }

    if (pages.isEmpty()) {
      pages = pagesViaRegex(xmlText);
    }

    List<WikiSection> sections = List.of();
    List<String> rootSections = List.of();
    if (comprehensive) {
      if (root != null) {
        var parsed = sectionsFromElements(descendants(root, "section"));
        sections = parsed.sections();
        rootSections = parsed.rootSections();
      } else {
        var parsed = sectionsViaRegex(xmlText);
        sections = parsed.sections();
        rootSections = parsed.rootSections();
      }
    }

    return new WikiStructure(WikiStructure.ID, title.strip(), description.strip(), pages, sections, rootSections);
  }

  private record SectionsResult(List<WikiSection> sections, List<String> rootSections) {}

  // DocumentBuilderFactory construction does a service-provider lookup and dominated
  // this parser's measured cost in bench/REPORT.md (~2.6x the source's own runtime for
  // the identical rule) until built once and reused; a DocumentBuilder from it is cheap
  // to create per call and is not required to be thread-safe for concurrent parse().
  private static final DocumentBuilderFactory STRICT_FACTORY = newStrictFactory();

  private static DocumentBuilderFactory newStrictFactory() {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return factory;
  }

  private static Element tryStrictParse(String xmlText) {
    try {
      DocumentBuilder builder = STRICT_FACTORY.newDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
    } catch (Exception e) {
      return null;
    }
  }

  private static List<Element> descendants(Element root, String tagName) {
    NodeList nodes = root.getElementsByTagName(tagName);
    List<Element> out = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) {
      out.add((Element) nodes.item(i));
    }
    return out;
  }

  private static String firstChildText(Element parent, String tagName) {
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node n = nodes.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(tagName)) {
        return n.getTextContent() == null ? "" : n.getTextContent();
      }
    }
    return "";
  }

  private static List<String> childTexts(Element el, String tagName) {
    List<String> out = new ArrayList<>();
    for (Element child : descendants(el, tagName)) {
      String text = child.getTextContent();
      if (text != null && !text.strip().isEmpty()) {
        out.add(text.strip());
      }
    }
    return out;
  }

  private static List<WikiPage> pagesFromElements(List<Element> elements) {
    List<WikiPage> pages = new ArrayList<>();
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);
      String id = idOrDefault(el, "page-" + (i + 1));
      pages.add(
          new WikiPage(
              id,
              firstChildText(el, "title").strip(),
              "",
              childTexts(el, "file_path"),
              normalizeImportance(firstChildText(el, "importance")),
              childTexts(el, "related")));
    }
    return pages;
  }

  private static List<WikiPage> pagesViaRegex(String xmlText) {
    List<WikiPage> pages = new ArrayList<>();
    Matcher blocks = PAGE_BLOCK.matcher(xmlText);
    int i = 0;
    while (blocks.find()) {
      String block = blocks.group();
      String id = firstGroupOrNull(PAGE_ID, block);
      String title = firstGroupOrNull(TITLE, block);
      String importance = firstGroupOrNull(IMPORTANCE, block);
      pages.add(
          new WikiPage(
              id != null ? id : "page-" + (i + 1),
              title != null ? title.strip() : "",
              "",
              allGroupsTrimmed(FILE_PATH, block),
              normalizeImportance(importance),
              allGroupsTrimmed(RELATED, block)));
      i++;
    }
    return pages;
  }

  private static SectionsResult sectionsFromElements(List<Element> elements) {
    List<WikiSection> sections = new ArrayList<>();
    Set<String> referenced = new LinkedHashSet<>();
    for (int i = 0; i < elements.size(); i++) {
      Element el = elements.get(i);
      String id = idOrDefault(el, "section-" + (i + 1));
      List<String> subs = childTexts(el, "section_ref");
      sections.add(new WikiSection(id, firstChildText(el, "title").strip(), childTexts(el, "page_ref"), subs.isEmpty() ? null : subs));
      referenced.addAll(subs);
    }
    List<String> rootSections = new ArrayList<>();
    for (WikiSection s : sections) {
      if (!referenced.contains(s.id())) {
        rootSections.add(s.id());
      }
    }
    return new SectionsResult(sections, rootSections);
  }

  private static SectionsResult sectionsViaRegex(String xmlText) {
    List<WikiSection> sections = new ArrayList<>();
    Set<String> referenced = new LinkedHashSet<>();
    Matcher blocks = SECTION_BLOCK.matcher(xmlText);
    int i = 0;
    while (blocks.find()) {
      String block = blocks.group();
      String id = firstGroupOrNull(SECTION_ID, block);
      String title = firstGroupOrNull(TITLE, block);
      List<String> pageRefs = allGroupsTrimmed(PAGE_REF, block);
      List<String> subs = allGroupsTrimmed(SECTION_REF, block);
      sections.add(new WikiSection(id != null ? id : "section-" + (i + 1), title != null ? title.strip() : "", pageRefs, subs.isEmpty() ? null : subs));
      referenced.addAll(subs);
      i++;
    }
    List<String> rootSections = new ArrayList<>();
    for (WikiSection s : sections) {
      if (!referenced.contains(s.id())) {
        rootSections.add(s.id());
      }
    }
    return new SectionsResult(sections, rootSections);
  }

  /** Python's {@code el.get("id") or default}: an empty attribute also falls back. */
  private static String idOrDefault(Element el, String fallback) {
    String id = el.hasAttribute("id") ? el.getAttribute("id") : "";
    return id.isEmpty() ? fallback : id;
  }

  private static String normalizeImportance(String value) {
    String v = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "high", "medium", "low" -> v;
      default -> "medium";
    };
  }

  private static String firstGroup(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(1).strip() : "";
  }

  private static String firstGroupOrNull(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  private static List<String> allGroupsTrimmed(Pattern pattern, String text) {
    List<String> out = new ArrayList<>();
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      String v = m.group(1).strip();
      if (!v.isEmpty()) {
        out.add(v);
      }
    }
    return out;
  }
}
