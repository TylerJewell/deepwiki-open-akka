package io.akka.deepwiki.api;

/**
 * Port of {@code WIKI_AUTH_MODE}/{@code WIKI_AUTH_CODE} (api/config.py:59-62): an optional
 * gate on {@code DELETE /api/wiki_cache}, the only route the source protects this way.
 */
final class WikiAuth {

  static final boolean MODE_ENABLED = parseBool(System.getenv("DEEPWIKI_AUTH_MODE"));
  static final String CODE = System.getenv().getOrDefault("DEEPWIKI_AUTH_CODE", "");

  private static boolean parseBool(String raw) {
    String v = (raw == null ? "False" : raw).toLowerCase();
    return v.equals("true") || v.equals("1") || v.equals("t");
  }

  private WikiAuth() {}
}
