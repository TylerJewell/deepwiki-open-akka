package io.akka.deepwiki.application;

import io.akka.deepwiki.domain.WikiTaskRequest;

/**
 * SPEC-001 §4.1: the language-model boundary this port does not reimplement. The source's
 * {@code research_chat} streams tokens from whichever provider/model the request names;
 * this port only needs the final text, since every rule in the deterministic contract
 * operates on the complete response (parsing, citation resolution, retry).
 */
public interface ContentGenerationClient {

  String generate(WikiTaskRequest request, String prompt);
}
