package io.akka.deepwiki.domain;

import java.util.Map;

/** SPEC-001 §2 / R14. Cache-file contents; the access token is never carried in it. */
public record WikiCacheData(
    WikiStructure wikiStructure, Map<String, WikiPage> generatedPages, RepoInfo repo, String provider, String model) {}
