package com.agentforge4j.starter.files;

import com.agentforge4j.runtime.command.FileSink;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link FileSink} registered by this Spring Boot starter when the application does not
 * define its own {@code FileSink} bean.
 *
 * <p>Generated file content from workflow steps is intentionally discarded. This keeps this
 * starter storage-agnostic: it does not choose a filesystem path, database, or object
 * store.
 *
 * <p>Applications that must persist agent-produced files should register a {@code FileSink} bean
 * (for example in a platform or API module) and implement the desired strategy: local disk under a
 * configured base directory, database BLOBs, S3, etc. That bean replaces this default via
 * {@code @ConditionalOnMissingBean(FileSink.class)} on the starter.
 */
public final class NoOpFileSink implements FileSink {

  private static final System.Logger log = System.getLogger(NoOpFileSink.class.getName());

  private final AtomicBoolean discardWarningLogged = new AtomicBoolean(false);

  /**
   * Drops {@code content} and emits a warning at most once per JVM until a custom
   * {@link com.agentforge4j.runtime.command.FileSink} bean replaces this implementation.
   */
  @Override
  public void write(String runId, String stepId, String path, String content) {
    if (discardWarningLogged.compareAndSet(false, true)) {
      log.log(System.Logger.Level.WARNING,
          "No FileSink bean is configured; generated file outputs will be discarded. "
              + "Provide a FileSink bean to persist workflow files.");
    }
  }
}
