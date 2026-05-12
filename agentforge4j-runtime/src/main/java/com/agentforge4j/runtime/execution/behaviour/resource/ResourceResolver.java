package com.agentforge4j.runtime.execution.behaviour.resource;

/**
 * Resolves workflow resources into text content under a strict safety contract.
 */
public interface ResourceResolver {

  /**
   * Resolves resource content for a user-configured resource path.
   *
   * @param resourcePath path requested by workflow definition
   * @return UTF-8 textual content
   * @throws IllegalArgumentException when the path is invalid or blocked by policy
   */
  String resolve(String resourcePath);
}
