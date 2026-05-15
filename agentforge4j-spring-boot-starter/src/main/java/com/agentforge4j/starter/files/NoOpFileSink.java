package com.agentforge4j.starter.files;

import com.agentforge4j.runtime.command.FileSink;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link FileSink} registered by the OSS Spring Boot starter when the application does not
 * define its own {@code FileSink} bean.
 *
 * <p>Generated file content from workflow steps is intentionally discarded. This keeps the OSS
 * starter storage-agnostic: the starter does not choose a filesystem path, database, or object
 * store.
 *
 * <p>Applications that must persist agent-produced files should register a {@code FileSink} bean
 * (for example in a platform or API module) and implement the desired strategy: local disk under a
 * configured base directory, database BLOBs, S3, etc. That bean replaces this default via
 * {@code @ConditionalOnMissingBean(FileSink.class)} on the starter.
 */
public final class NoOpFileSink implements FileSink {

  private static final Logger log = LoggerFactory.getLogger(NoOpFileSink.class);

  private final AtomicBoolean discardWarningLogged = new AtomicBoolean(false);

  @Override
  public void write(String runId, String stepId, String path, String content) {
    if (discardWarningLogged.compareAndSet(false, true)) {
      log.warn(
          "No FileSink bean is configured; generated file outputs will be discarded. "
              + "Provide a FileSink bean to persist workflow files.");
    }
  }
}
