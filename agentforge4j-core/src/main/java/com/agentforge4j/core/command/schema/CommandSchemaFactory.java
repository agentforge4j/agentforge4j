// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command.schema;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;

/**
 * Builds a {@link CommandResponseSchema} from an agent's {@code supportedCommands}, using Jackson
 * introspection on the sealed {@link LlmCommand} implementations (no duplicated field lists).
 */
public final class CommandSchemaFactory {

  /**
   * Command type advertised to an agent only when it explicitly lists it in
   * {@code supportedCommands}; excluded from the all-commands default so existing agents are
   * unaffected and the runtime never receives a {@code TOOL_INVOCATION} for an unconfigured tool
   * path.
   */
  private static final String OPT_IN_TOOL_INVOCATION = "TOOL_INVOCATION";

  private CommandSchemaFactory() {
  }

  /**
   * Builds a command response schema for the given supported commands using Jackson introspection.
   *
   * @param supportedCommands list of command type names to include, or null/empty for all
   * @param mapper            Jackson ObjectMapper for introspection
   *
   * @return the constructed schema
   */
  public static CommandResponseSchema build(List<String> supportedCommands, ObjectMapper mapper) {
    Validate.notNull(mapper, "mapper must not be null");
    Map<String, Class<? extends LlmCommand>> registry = LlmCommandSubtypeRegistry.allSubtypes();
    List<String> effective = resolveSupported(supportedCommands, registry);
    List<CommandTypeContract> contracts = new ArrayList<>();
    for (String typeName : effective) {
      Class<? extends LlmCommand> impl = registry.get(typeName);
      contracts.add(new CommandTypeContract(
          typeName,
          impl,
          List.copyOf(requiredJsonPropertyNames(mapper, impl))));
    }
    String cacheKey =
        CommandResponseSchema.COMMAND_SCHEMA_VERSION + "|" + String.join(",", effective);
    return new CommandResponseSchema(
        CommandResponseSchema.COMMAND_SCHEMA_VERSION,
        effective,
        contracts,
        cacheKey);
  }

  private static List<String> resolveSupported(List<String> supportedCommands,
      Map<String, Class<? extends LlmCommand>> registry) {
    if (supportedCommands == null || supportedCommands.isEmpty()) {
      return registry.keySet().stream()
          .filter(name -> !OPT_IN_TOOL_INVOCATION.equals(name))
          .toList();
    }
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String raw : supportedCommands) {
      if (StringUtils.isBlank(raw)) {
        continue;
      }
      String name = raw.strip();
      Validate.isTrue(registry.containsKey(name),
          "Unknown supportedCommand '%s'. Known types: %s".formatted(name, registry.keySet()));
      unique.add(name);
    }
    return List.copyOf(
        Validate.notEmpty(unique, "supportedCommands must name at least one valid command type"));
  }

  private static List<String> requiredJsonPropertyNames(ObjectMapper mapper,
      Class<?> commandClass) {
    JavaType javaType = mapper.constructType(commandClass);
    BeanDescription desc = mapper.getDeserializationConfig().introspect(javaType);
    TreeSet<String> sorted = new TreeSet<>();
    for (BeanPropertyDefinition def : desc.findProperties()) {
      if (!def.couldDeserialize()) {
        continue;
      }
      if ("type".equals(def.getName())) {
        continue;
      }
      if (Boolean.TRUE.equals(def.getMetadata().getRequired())) {
        sorted.add(def.getName());
      }
    }
    return new ArrayList<>(sorted);
  }
}
