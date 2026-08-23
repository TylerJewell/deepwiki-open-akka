package io.akka.deepwiki.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Found by actually running the service (`akka_local_request`), not by reading the code:
 * Jackson serializes a bare Java enum as its `name()` (uppercase) by default, but the
 * source's own wire contract is a lowercase string enum (api/schemas/{base,repo}.py). A
 * client written against the source's API would see "LOCAL"/"COMPLETED" instead of
 * "local"/"completed" without {@code @JsonValue}/{@code @JsonCreator} on these two enums.
 */
class WireFormatTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void repoTypeSerializesLowercase() throws Exception {
    assertEquals("\"local\"", mapper.writeValueAsString(RepoType.LOCAL));
    assertEquals(RepoType.GITHUB, mapper.readValue("\"github\"", RepoType.class));
  }

  @Test
  void taskStatusSerializesLowercase() throws Exception {
    assertEquals("\"completed\"", mapper.writeValueAsString(TaskStatus.COMPLETED));
    assertEquals(TaskStatus.DETERMINING_STRUCTURE, mapper.readValue("\"determining_structure\"", TaskStatus.class));
  }
}
