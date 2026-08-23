package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SPEC-001 R1-R3. Mirrors tests/test_extract_repo_name.py and tests/test_repository.py. */
class RepoIdentityTest {

  @ParameterizedTest
  @CsvSource({"https://github.com/owner/repo, owner_repo", "https://github.com/owner/repo.git, owner_repo", "https://github.com/owner/repo/, owner_repo", "https://github.com/repo, repo"})
  void githubStandardUrl(String url, String expectedName) {
    RepoIdentity repo = new RepoIdentity(url, RepoType.GITHUB, "/root");
    assertEquals(expectedName, repo.name());
    assertFalse(repo.isLocal());
  }

  @ParameterizedTest
  @CsvSource({"https://gitlab.com/owner/repo, owner_repo", "https://gitlab.com/group/subgroup/repo, subgroup_repo"})
  void gitlabUrls(String url, String expectedName) {
    RepoIdentity repo = new RepoIdentity(url, RepoType.GITLAB, "/root");
    assertEquals(expectedName, repo.name());
    assertFalse(repo.isLocal());
  }

  @Test
  void bitbucketUrl() {
    RepoIdentity repo = new RepoIdentity("https://bitbucket.org/owner/repo", RepoType.BITBUCKET, "/root");
    assertEquals("owner_repo", repo.name());
    assertFalse(repo.isLocal());
  }

  @ParameterizedTest
  @CsvSource({"/home/user/projects/my-repo, my-repo", "/var/repos/project.git, project.git", "my-repo, my-repo"})
  void localPaths(String path, String expectedName) {
    RepoIdentity repo = new RepoIdentity(path, RepoType.LOCAL, "/root");
    assertEquals(expectedName, repo.name());
    assertTrue(repo.isLocal());
  }

  @Test
  void savePathForRemote() {
    RepoIdentity repo = new RepoIdentity("https://github.com/AsyncFuncAI/deepwiki-open", RepoType.GITHUB, "/clone-root");
    assertEquals("/clone-root/AsyncFuncAI_deepwiki-open", repo.savePath());
  }

  @Test
  void savePathForLocalIsUnchanged() {
    RepoIdentity repo = new RepoIdentity("./", RepoType.LOCAL, "/clone-root");
    assertTrue(repo.isLocal());
    assertEquals("./", repo.savePath());
  }
}
