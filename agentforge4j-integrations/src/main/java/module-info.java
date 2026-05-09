/**
 * Controlled abstractions for external tools and side-effecting operations used during runs.
 *
 * <p>Integrations are invoked by the runtime on its terms so calls remain deterministic, auditable,
 * and policy-bound. This module is the plugin boundary for side-effecting external operations
 * without leaking vendor SDKs into {@code agentforge4j.core}.
 *
 * <p>Consumers: runtime orchestration and provider adapters that need a narrow, testable integration API.
 */
module agentforge4j.integrations {
    requires agentforge4j.util;
    exports com.agentforge4j.integrations;
}
