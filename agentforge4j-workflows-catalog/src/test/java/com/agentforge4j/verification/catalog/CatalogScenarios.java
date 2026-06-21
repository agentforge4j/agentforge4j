// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Discovers, runs, and asserts the data-driven shipped-catalog scenarios that each shipped workflow
 * <em>owns</em> locally. A scenario lives next to its workflow under
 * {@code /shipped-workflows/<id>.workflow/verification/} as a {@code script.json} (fake-llm script),
 * an {@code expected-result.json} ({@link ExpectedResult}), and a {@code README.md}. There is no
 * central scenario registry: discovery walks the real shipped-workflow catalog on the classpath, so a
 * workflow folder carries its verification scenario with it (and loses it when the folder is removed).
 *
 * <p>Discovery is jar-safe — the catalog resources are served from this catalog module's own
 * artifact, a jar on the test classpath — so it resolves the {@code /shipped-workflows} root
 * for both {@code file:} (exploded) and {@code jar:} classpath layouts. A workflow folder is treated
 * as a scenario owner when it carries a {@code verification/expected-result.json}. The
 * {@link com.agentforge4j.config.loader.workflow.ClasspathWorkflowLoader production loader} only reads
 * a bundle's {@code index}-listed entries plus {@code workflow.json}, so the (unindexed)
 * {@code verification/} folder is inert to production loading.
 *
 * <p>The runner drives the <em>real</em> shipped workflow through the testkit harness with fake
 * responses and the scenario's scripted human gates, then projects the expectations onto
 * {@link WorkflowRunAssert}.
 */
public final class CatalogScenarios {

  private static final String SHIPPED_WORKFLOWS_ROOT = "/shipped-workflows";
  private static final String WORKFLOW_SUFFIX = ".workflow";
  private static final String VERIFICATION_SUBDIR = "verification";
  private static final String SCRIPT_FILE = "script.json";
  private static final String EXPECTED_RESULT_FILE = "expected-result.json";
  private static final String README_FILE = "README.md";
  /** Matches {@code shipped-workflows/<id>.workflow/verification/expected-result.json} jar entries. */
  private static final Pattern JAR_OWNER_ENTRY = Pattern.compile(
      "^shipped-workflows/([^/]+)\\.workflow/" + VERIFICATION_SUBDIR + "/"
          + Pattern.quote(EXPECTED_RESULT_FILE) + "$");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CatalogScenarios() {
  }

  /**
   * Discovers every scenario the shipped-workflow catalog owns, one per workflow folder that carries
   * a {@code verification/expected-result.json}.
   *
   * @return the discovered scenarios, sorted by owning workflow id (empty if the catalog is absent)
   */
  public static List<ScenarioCase> discover() {
    List<ScenarioCase> cases = new ArrayList<>();
    for (String workflowId : scenarioOwningWorkflowIds()) {
      cases.add(load(workflowId));
    }
    cases.sort(Comparator.comparing(ScenarioCase::name));
    return cases;
  }

  /**
   * Enumerates the ids of shipped-workflow folders that own a verification scenario, read straight
   * from the catalog tree on the classpath (no central registry, no hard-coded list).
   *
   * @return owning workflow ids (empty if the catalog root is absent)
   */
  public static Set<String> scenarioOwningWorkflowIds() {
    URL root = CatalogScenarios.class.getResource(SHIPPED_WORKFLOWS_ROOT);
    if (root == null) {
      return Set.of();
    }
    return switch (root.getProtocol()) {
      case "file" -> owningIdsFromDirectory(root);
      case "jar" -> owningIdsFromJar(root);
      default -> throw new IllegalStateException(
          "Unsupported shipped-workflows catalog URL protocol: " + root);
    };
  }

  /**
   * Reads a scenario's raw {@code expected-result.json} from its owning workflow folder.
   *
   * @param workflowId the owning shipped workflow id
   *
   * @return the raw JSON text
   */
  public static String readExpectedResultJson(String workflowId) {
    return readResource(verificationPath(workflowId) + EXPECTED_RESULT_FILE, workflowId);
  }

  private static Set<String> owningIdsFromDirectory(URL root) {
    try {
      Path rootDir = Path.of(root.toURI());
      try (Stream<Path> entries = Files.list(rootDir)) {
        Set<String> ids = new LinkedHashSet<>();
        entries
            .filter(Files::isDirectory)
            .filter(dir -> dir.getFileName().toString().endsWith(WORKFLOW_SUFFIX))
            .filter(dir -> Files.exists(
                dir.resolve(VERIFICATION_SUBDIR).resolve(EXPECTED_RESULT_FILE)))
            .forEach(dir -> ids.add(workflowIdOf(dir.getFileName().toString())));
        return ids;
      }
    } catch (URISyntaxException | IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate shipped-workflows catalog at " + root, new IOException(exception));
    }
  }

  private static Set<String> owningIdsFromJar(URL root) {
    try {
      JarURLConnection connection = (JarURLConnection) root.openConnection();
      connection.setUseCaches(true);
      // The JarFile is owned by the URL connection cache; it is deliberately not closed here.
      JarFile jar = connection.getJarFile();
      Set<String> ids = new LinkedHashSet<>();
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        Matcher matcher = JAR_OWNER_ENTRY.matcher(entries.nextElement().getName());
        if (matcher.matches()) {
          ids.add(matcher.group(1));
        }
      }
      return ids;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate shipped-workflows catalog jar at " + root, exception);
    }
  }

  private static ScenarioCase load(String workflowId) {
    String base = verificationPath(workflowId);
    String scriptJson = readResource(base + SCRIPT_FILE, workflowId);
    ExpectedResult expected;
    try {
      expected = MAPPER.readValue(readResource(base + EXPECTED_RESULT_FILE, workflowId),
          ExpectedResult.class);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to parse " + base + EXPECTED_RESULT_FILE, exception);
    }
    boolean readme = CatalogScenarios.class.getResource(base + README_FILE) != null;
    return new ScenarioCase(workflowId, scriptJson, expected, readme);
  }

  private static String verificationPath(String workflowId) {
    return SHIPPED_WORKFLOWS_ROOT + "/" + workflowId + WORKFLOW_SUFFIX + "/" + VERIFICATION_SUBDIR
        + "/";
  }

  private static String workflowIdOf(String folderName) {
    return folderName.substring(0, folderName.length() - WORKFLOW_SUFFIX.length());
  }

  private static String readResource(String classpathPath, String workflowId) {
    try (InputStream stream = CatalogScenarios.class.getResourceAsStream(classpathPath)) {
      if (stream == null) {
        throw new IllegalStateException(
            "Shipped workflow '%s' is missing its verification resource: %s"
                .formatted(workflowId, classpathPath));
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read " + classpathPath, exception);
    }
  }

  /**
   * Runs a scenario: parses its fake script, builds its gate queue, drives the real shipped workflow
   * through the harness, and returns the captured result.
   *
   * @param scenario the scenario to run
   *
   * @return the captured run result
   */
  public static WorkflowRunResult run(ScenarioCase scenario) {
    FakeScript script = new FakeScriptParser().parse(scenario.scriptJson());
    List<GateResponse> gates = toGateResponses(scenario.expected().gates());
    WorkflowTestHarness harness =
        WorkflowTestHarness.builder().shippedCatalog(true).script(script).build();
    return harness.run(scenario.expected().workflowId(), gates);
  }

  /**
   * Projects a scenario's expectations onto fluent run assertions. A {@code null} expectation field
   * is not asserted.
   *
   * @param result the captured run result
   * @param expect the assertions to apply
   */
  public static void assertExpectations(WorkflowRunResult result, ExpectedResult.ExpectSpec expect) {
    if (expect == null) {
      return;
    }
    WorkflowRunAssert assertion = WorkflowRunAssert.assertThat(result);
    if (expect.status() != null) {
      assertion.hasStatus(WorkflowStatus.valueOf(expect.status()));
    }
    if (expect.context() != null) {
      expect.context().forEach(assertion::contextEquals);
    }
    if (expect.visitedSteps() != null) {
      expect.visitedSteps().forEach(assertion::visitedStep);
    }
    if (expect.notVisitedSteps() != null) {
      expect.notVisitedSteps().forEach(assertion::didNotVisitStep);
    }
    if (expect.emittedEvents() != null) {
      expect.emittedEvents().forEach(name -> assertion.emittedEvent(WorkflowEventType.valueOf(name)));
    }
    if (expect.createdFiles() != null) {
      expect.createdFiles().forEach(assertion::createdFile);
    }
  }

  private static List<GateResponse> toGateResponses(List<ExpectedResult.GateSpec> specs) {
    if (specs == null) {
      return List.of();
    }
    List<GateResponse> responses = new ArrayList<>();
    for (ExpectedResult.GateSpec spec : specs) {
      responses.add(toGateResponse(spec));
    }
    return responses;
  }

  private static GateResponse toGateResponse(ExpectedResult.GateSpec spec) {
    String toolId = spec.toolInvocationId();
    return switch (spec.type()) {
      case "input" -> GateResponse.input(spec.answers() == null ? Map.of() : spec.answers());
      case "review" -> GateResponse.review(spec.note());
      case "stepApproval" -> Boolean.TRUE.equals(spec.approve())
          ? GateResponse.approveStep(spec.note())
          : GateResponse.rejectStep(spec.note());
      case "escalation" -> GateResponse.escalationApproval(spec.note());
      case "toolApprove" -> toolId == null
          ? GateResponse.toolApprove()
          : GateResponse.toolApprove(toolId);
      case "toolReject" -> toolId == null
          ? GateResponse.toolReject(spec.reason())
          : GateResponse.toolReject(toolId, spec.reason());
      case "toolContinue" -> toolId == null
          ? GateResponse.toolContinue()
          : GateResponse.toolContinue(toolId);
      case "toolRetry" -> toolId == null
          ? GateResponse.toolRetry()
          : GateResponse.toolRetry(toolId);
      default -> throw new IllegalArgumentException("Unknown gate type: " + spec.type());
    };
  }
}
