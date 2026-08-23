package io.akka.deepwiki.domain;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Port of {@code _clone_from_github}/{@code _clone_from_gitlab}/{@code _clone_from_bitbucket}
 * plus {@code Repo.download} (api/repository.py:38-217): token-URL rewriting per host, then a
 * shallow single-branch clone. The clone subprocess itself is the network boundary
 * (SPEC-001 §1); the rewriting rules are exactly what is under test here, and are exercised
 * end-to-end against a local (non-network) git remote.
 */
public final class RepoCloner {

  private RepoCloner() {}

  /** SPEC-001 (repository ingestion): GitHub token goes in as {@code token@host}. */
  public static String rewriteGithubUrl(String remoteUrl, String accessToken) {
    if (accessToken == null || accessToken.isEmpty()) {
      return remoteUrl;
    }
    URI uri = URI.create(remoteUrl);
    return withUserInfo(uri, accessToken);
  }

  /** GitLab token goes in as {@code oauth2:<url-encoded-token>@host}. */
  public static String rewriteGitlabUrl(String remoteUrl, String accessToken) {
    if (accessToken == null || accessToken.isEmpty()) {
      return remoteUrl;
    }
    URI uri = URI.create(remoteUrl);
    String encoded = URLEncoder.encode(accessToken, StandardCharsets.UTF_8).replace("+", "%20");
    return withUserInfo(uri, "oauth2:" + encoded);
  }

  /**
   * Bitbucket: an HTTP access token (prefix {@code ATCTT}) uses
   * {@code x-bitbucket-api-token-auth}; a (deprecated) app password uses {@code x-token-auth}.
   */
  public static String rewriteBitbucketUrl(String remoteUrl, String accessToken) {
    if (accessToken == null || accessToken.isEmpty()) {
      return remoteUrl;
    }
    URI uri = URI.create(remoteUrl);
    String authScheme = accessToken.startsWith("ATCTT") ? "x-bitbucket-api-token-auth" : "x-token-auth";
    String encoded = URLEncoder.encode(accessToken, StandardCharsets.UTF_8).replace("+", "%20");
    return withUserInfo(uri, authScheme + ":" + encoded);
  }

  private static String withUserInfo(URI uri, String userInfo) {
    return uri.getScheme() + "://" + userInfo + "@" + uri.getHost() + uri.getPath();
  }

  /** Shallow, single-branch clone. Callers pass an already-rewritten URL. */
  public static void clone(String remoteUrl, Path localPath) {
    clone(remoteUrl, localPath, null);
  }

  /**
   * Port of {@code _exception_cleanup} (api/repository.py:18-35): a failing clone's error
   * message never contains the raw or URL-encoded access token, only {@code ***TOKEN***}.
   */
  private static void clone(String remoteUrl, Path localPath, String accessToken) {
    try {
      Files.createDirectories(localPath.getParent() == null ? localPath : localPath.getParent());
      ProcessBuilder pb =
          new ProcessBuilder("git", "clone", "--depth=1", "--single-branch", remoteUrl, localPath.toString());
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      if (exit != 0) {
        throw new IllegalStateException(maskToken(output, accessToken));
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException(e);
    }
  }

  static String maskToken(String message, String accessToken) {
    if (accessToken == null || accessToken.isEmpty()) {
      return message;
    }
    String encoded = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
    return message.replace(accessToken, "***TOKEN***").replace(encoded, "***TOKEN***");
  }

  public static void cloneFor(RepoType type, String remoteUrl, String accessToken, Path localPath) {
    String rewritten =
        switch (type) {
          case GITHUB -> rewriteGithubUrl(remoteUrl, accessToken);
          case GITLAB -> rewriteGitlabUrl(remoteUrl, accessToken);
          case BITBUCKET -> rewriteBitbucketUrl(remoteUrl, accessToken);
          case LOCAL -> throw new IllegalArgumentException("Cannot clone a local repo type");
        };
    clone(rewritten, localPath, accessToken);
  }
}
