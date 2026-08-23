package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Port of the URL-rewriting rules in {@code _clone_from_github}/{@code _clone_from_gitlab}/
 * {@code _clone_from_bitbucket} (api/repository.py:38-118), and the token-masking rule in
 * {@code _exception_cleanup} (api/repository.py:18-35), found missing during the review
 * pass and added then (docs/review-findings.md).
 */
class RepoClonerTest {

  @Test
  void githubTokenGoesInAsUserinfo() {
    assertEquals("https://ghp_abc123@github.com/o/r", RepoCloner.rewriteGithubUrl("https://github.com/o/r", "ghp_abc123"));
  }

  @Test
  void githubWithoutTokenIsUnchanged() {
    assertEquals("https://github.com/o/r", RepoCloner.rewriteGithubUrl("https://github.com/o/r", null));
  }

  @Test
  void gitlabTokenUsesOauth2SchemeAndUrlEncoding() {
    assertEquals("https://oauth2:a%2Fb@gitlab.com/o/r", RepoCloner.rewriteGitlabUrl("https://gitlab.com/o/r", "a/b"));
  }

  @Test
  void bitbucketHttpAccessTokenUsesApiTokenAuthScheme() {
    assertEquals(
        "https://x-bitbucket-api-token-auth:ATCTT123@bitbucket.org/o/r",
        RepoCloner.rewriteBitbucketUrl("https://bitbucket.org/o/r", "ATCTT123"));
  }

  @Test
  void bitbucketAppPasswordUsesTokenAuthScheme() {
    assertEquals(
        "https://x-token-auth:legacy-pw@bitbucket.org/o/r", RepoCloner.rewriteBitbucketUrl("https://bitbucket.org/o/r", "legacy-pw"));
  }

  @Test
  void maskTokenReplacesRawAndUrlEncodedForms() {
    String masked = RepoCloner.maskToken("fatal: could not read 'a/b' is not a valid token", "a/b");
    assertFalse(masked.contains("a/b"));
    assertTrue(masked.contains("***TOKEN***"));

    String maskedEncoded = RepoCloner.maskToken("fatal: could not read 'a%2Fb' is not a valid token", "a/b");
    assertFalse(maskedEncoded.contains("a%2Fb"));
    assertTrue(maskedEncoded.contains("***TOKEN***"));
  }

  @Test
  void maskTokenWithNoTokenIsUnchanged() {
    assertEquals("plain error", RepoCloner.maskToken("plain error", null));
  }
}
