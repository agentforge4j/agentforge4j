package com.agentforge4j.core.workflow.repository;

import com.agentforge4j.core.workflow.file.WorkflowFile;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link WorkflowFile} metadata and content addressing.
 */
public interface WorkflowFileRepository {

  /**
   * Inserts or replaces {@code file} in the backing store.
   */
  void save(WorkflowFile file);

  /**
   * Returns all files associated with {@code runId}, possibly empty.
   *
   * @param runId non-null run identifier understood by the implementation
   */
  List<WorkflowFile> findByRunId(String runId);

  /**
   * Returns one file when both ids match an existing row or object.
   *
   * @param runId  owning run id
   * @param fileId file id scoped to that run
   */
  Optional<WorkflowFile> findByRunIdAndFileId(String runId, String fileId);
}
