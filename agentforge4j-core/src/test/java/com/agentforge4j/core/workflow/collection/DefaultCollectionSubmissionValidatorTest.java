// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultCollectionSubmissionValidatorTest {

  private final DefaultCollectionSubmissionValidator validator =
      new DefaultCollectionSubmissionValidator();

  @Test
  void admitsInlineOnlyPayload() {
    assertThat(validator.validate(context(new CollectionPayload("{\"v\":1}", List.of())))
        .allowed()).isTrue();
  }

  @Test
  void admitsRelativeFilePaths() {
    CollectionPayload payload = new CollectionPayload(null, List.of(
        fileRef("cv.pdf"),
        fileRef("nested/dir/cv.pdf"),
        fileRef("a..b/notes.txt")));

    assertThat(validator.validate(context(payload)).allowed()).isTrue();
  }

  @Test
  void deniesUpwardTraversalSegments() {
    assertDenied("../escape.txt");
    assertDenied("a/../../escape.txt");
    assertDenied("..\\escape.txt");
  }

  @Test
  void deniesUpwardTraversalSegmentsWithSurroundingWhitespace() {
    assertDenied(".. /escape.txt");
    assertDenied(" ../escape.txt");
    assertDenied("a/.. /escape.txt");
  }

  @Test
  void deniesAbsoluteAndQualifiedPaths() {
    assertDenied("/etc/passwd");
    assertDenied("\\\\share\\file.txt");
    assertDenied("C:\\Windows\\file.txt");
    assertDenied("file:whatever");
  }

  @Test
  void deniesEmbeddedNulCharacter() {
    assertDenied("cv\0.pdf");
  }

  private void assertDenied(String path) {
    Decision decision = validator.validate(context(new CollectionPayload(null, List.of(fileRef(path)))));
    assertThat(decision.allowed()).as("path '%s' must be denied", path).isFalse();
    assertThat(decision.reason()).contains("FILE_PATH_UNSAFE");
  }

  private static FileRef fileRef(String path) {
    return new FileRef(path, "cv.pdf", "application/pdf", 10);
  }

  private static CollectionSubmissionContext context(CollectionPayload payload) {
    CollectionBehaviour behaviour = new CollectionBehaviour(null, 0, null, null, 0, null,
        DuplicatePolicy.ALLOW, ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, false,
        ReopenPolicy.NONE, AuthorizationMode.OPEN, StepTransition.AUTO);
    CollectionState collection = new CollectionState("step-1", CollectionPhase.OPEN, Instant.EPOCH,
        List.of(), null, null, null, null, null, 0);
    return new CollectionSubmissionContext("run-1", "wf-1", "step-1", "actor-1", payload, null,
        null, null, behaviour, collection);
  }
}
