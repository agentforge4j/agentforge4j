// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

/**
 * Confidence in an execution estimate, driven by structural predictability and envelope width.
 * {@link #HIGH} is reachable structurally with no historical calibration; hints and calibration
 * improve confidence and tighten ranges but gate no grade.
 */
public enum ConfidenceGrade {
  HIGH,
  MEDIUM,
  LOW,
  VERY_LOW
}
