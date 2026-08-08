// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.contextpack;

import java.util.List;

/**
 * Loads {@link ContextPack}s from an external source (for example a {@code context-packs} directory).
 * Pure contract; implementations live downstream.
 */
public interface ContextPackLoader {

  /**
   * Loads the configured context packs.
   *
   * @return the loaded packs; never {@code null}
   */
  List<ContextPack> load();
}
