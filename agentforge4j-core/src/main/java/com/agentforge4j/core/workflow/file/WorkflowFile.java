package com.agentforge4j.core.workflow.file;

import com.agentforge4j.util.Validate;

import java.time.Instant;

/**
 * Metadata and addressing for a file produced or consumed during a workflow run.
 *
 * @param fileId      non-blank unique id of the file within the run
 * @param runId       non-blank owning run id
 * @param stepId      non-blank step that created or references the file
 * @param fileName    non-blank logical file name
 * @param filePath    non-blank storage path or URI understood by
 *                    {@link com.agentforge4j.core.workflow.repository.WorkflowFileRepository}
 * @param contentType MIME type or format label; may be blank if unknown
 * @param sizeBytes   non-negative size in bytes
 * @param createdAt   non-null creation timestamp
 */
public record WorkflowFile(
    String fileId,
    String runId,
    String stepId,
    String fileName,
    String filePath,
    String contentType,
    long sizeBytes,
    Instant createdAt
) {

  public WorkflowFile {
    Validate.notBlank(fileId, "fileId must not be blank");
    Validate.notBlank(runId, "runId must not be blank for fileId: %s".formatted(fileId));
    Validate.notBlank(stepId, "stepId must not be blank for fileId: %s".formatted(stepId));
    Validate.notBlank(fileName, "fileName must not be blank for fileId: %s".formatted(fileName));
    Validate.notBlank(filePath, "filePath must not be blank  for fileId: %s".formatted(filePath));
    Validate.notNull(createdAt, "createdAt must not be null  for fileId: %s".formatted(createdAt));
    Validate.isNotNegative(sizeBytes,
        "sizeBytes must not be negative for fileId: %s".formatted(sizeBytes));
  }
}
