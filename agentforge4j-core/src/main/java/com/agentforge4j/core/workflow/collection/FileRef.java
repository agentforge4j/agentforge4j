// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Reference to a file submitted into a collection gate. {@code core} stores the reference only and
 * never reads file bytes; the host writes content through the existing {@code FileSink} and addresses
 * it by the run/step coordinate plus {@code path}. There is deliberately no opaque file id and no
 * content digest — neither exists on the underlying file model.
 *
 * @param path        non-blank storage path understood by the host's {@code FileSink} for the owning
 *                    run and step
 * @param filename    non-blank logical file name
 * @param contentType MIME type or format label; may be {@code null} or blank when unknown
 * @param sizeBytes   non-negative size in bytes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileRef(
    String path,
    String filename,
    String contentType,
    long sizeBytes
) {

  public FileRef {
    Validate.notBlank(path, "FileRef path must not be blank");
    Validate.notBlank(filename, "FileRef filename must not be blank");
    Validate.isNotNegative(sizeBytes, "FileRef sizeBytes must not be negative");
  }
}
