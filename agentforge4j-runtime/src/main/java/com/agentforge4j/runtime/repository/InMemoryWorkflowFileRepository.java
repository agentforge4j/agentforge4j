package com.agentforge4j.runtime.repository;

import com.agentforge4j.core.workflow.file.WorkflowFile;
import com.agentforge4j.core.workflow.repository.WorkflowFileRepository;
import com.agentforge4j.util.Validate;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link WorkflowFileRepository} keyed by {@link com.agentforge4j.core.workflow.file.WorkflowFile#fileId()}.
 */
public final class InMemoryWorkflowFileRepository implements WorkflowFileRepository {

  private final ConcurrentMap<String, WorkflowFile> filesById = new ConcurrentHashMap<>();

  /** Creates an empty repository. */
  public InMemoryWorkflowFileRepository() {
  }

  @Override
  public void save(WorkflowFile file) {
    Validate.notNull(file, "file must not be null");
    filesById.put(file.fileId(), file);
  }

  @Override
  public List<WorkflowFile> findByRunId(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    return filesById.values().stream()
        .filter(file -> runId.equals(file.runId()))
        .sorted(Comparator.comparing(WorkflowFile::createdAt))
        .toList();
  }

  @Override
  public Optional<WorkflowFile> findByRunIdAndFileId(String runId, String fileId) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(fileId, "fileId must not be blank");
    WorkflowFile workflowFile = filesById.get(fileId);
    if (workflowFile == null) {
      return Optional.empty();
    }
    if (!runId.equals(workflowFile.runId())) {
      return Optional.empty();
    }
    return Optional.of(workflowFile);
  }
}
