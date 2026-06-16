// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import java.util.List;

/**
 * Signal that the current step should proceed to the next iteration or phase.
 *
 * <p>During {@code SPAR} exchange rounds (not the final resolution call), agents may add optional
 * fields that declare whether another sparring round is justified. The runtime continues only when
 * at least one side sets {@code wantsAnotherRound} to {@code true} and supplies a concrete,
 * non-vague {@code reason}. These fields are ignored for ordinary (non-SPAR) steps and for SPAR
 * resolution, where normal command application applies.
 *
 * @param wantsAnotherRound      when {@code true}, asks for another SPAR round (subject to
 *                               runtime validation of {@code reason}); {@code null} or {@code false}
 *                               means no continuation request
 * @param reason                  free-text justification when requesting another round; may be
 *                               {@code null} when not requesting continuation
 * @param unresolvedConcerns     optional bullet list of concrete open issues; may be {@code null}
 */
public record ContinueCommand(
    Boolean wantsAnotherRound,
    String reason,
    List<String> unresolvedConcerns) implements LlmCommand {

  public ContinueCommand {
    unresolvedConcerns =
        unresolvedConcerns == null || unresolvedConcerns.isEmpty()
            ? null
            : List.copyOf(unresolvedConcerns);
  }
}
