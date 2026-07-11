// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.CollectionOp;
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
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CatalogScenarios() {
  }

  /** One discovered scenario location: the owning workflow folder id and the scenario sub-folder. */
  private record ScenarioRef(String workflowId, String scenarioName) {
  }

  /**
   * Discovers every scenario the shipped-workflow catalog owns. A workflow folder may own several
   * scenarios: each lives under its own {@code verification/<scenario>/} sub-folder carrying an
   * {@code expected-result.json}.
   *
   * @return the discovered scenarios, sorted by display name (empty if the catalog is absent)
   */
  public static List<ScenarioCase> discover() {
    return discoverFrom(SHIPPED_WORKFLOWS_ROOT);
  }

  /**
   * Enumerates the ids of shipped-workflow folders that own at least one verification scenario, read
   * straight from the catalog tree on the classpath (no central registry, no hard-coded list).
   *
   * @return owning workflow ids (empty if the catalog root is absent)
   */
  public static Set<String> scenarioOwningWorkflowIds() {
    return owningWorkflowIds(SHIPPED_WORKFLOWS_ROOT);
  }

  /**
   * Discovers scenarios under an arbitrary classpath root. Package-private so tests can exercise the
   * 1:N discovery against a fixture tree without polluting the real {@code /shipped-workflows} root.
   *
   * @param classpathRoot absolute classpath root, e.g. {@code /shipped-workflows}
   *
   * @return the discovered scenarios, sorted by display name
   */
  static List<ScenarioCase> discoverFrom(String classpathRoot) {
    List<ScenarioCase> cases = new ArrayList<>();
    for (ScenarioRef ref : scenarioRefs(classpathRoot)) {
      cases.add(load(classpathRoot, ref));
    }
    cases.sort(Comparator.comparing(ScenarioCase::name));
    return cases;
  }

  static Set<String> owningWorkflowIds(String classpathRoot) {
    Set<String> ids = new LinkedHashSet<>();
    for (ScenarioRef ref : scenarioRefs(classpathRoot)) {
      ids.add(ref.workflowId());
    }
    return ids;
  }

  private static List<ScenarioRef> scenarioRefs(String classpathRoot) {
    URL root = CatalogScenarios.class.getResource(classpathRoot);
    if (root == null) {
      return List.of();
    }
    return switch (root.getProtocol()) {
      case "file" -> refsFromDirectory(root);
      case "jar" -> refsFromJar(classpathRoot, root);
      default -> throw new IllegalStateException(
          "Unsupported shipped-workflows catalog URL protocol: " + root);
    };
  }

  private static List<ScenarioRef> refsFromDirectory(URL root) {
    try {
      Path rootDir = Path.of(root.toURI());
      List<ScenarioRef> refs = new ArrayList<>();
      List<Path> workflowDirs;
      try (Stream<Path> entries = Files.list(rootDir)) {
        workflowDirs = entries
            .filter(Files::isDirectory)
            .filter(dir -> dir.getFileName().toString().endsWith(WORKFLOW_SUFFIX))
            .sorted()
            .toList();
      }
      for (Path workflowDir : workflowDirs) {
        String workflowId = workflowIdOf(workflowDir.getFileName().toString());
        Path verificationDir = workflowDir.resolve(VERIFICATION_SUBDIR);
        if (!Files.isDirectory(verificationDir)) {
          continue;
        }
        try (Stream<Path> scenarioDirs = Files.list(verificationDir)) {
          scenarioDirs
              .filter(Files::isDirectory)
              .filter(dir -> Files.exists(dir.resolve(EXPECTED_RESULT_FILE)))
              .sorted()
              .forEach(dir -> refs.add(new ScenarioRef(workflowId, dir.getFileName().toString())));
        }
      }
      return refs;
    } catch (URISyntaxException | IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate shipped-workflows catalog at " + root, new IOException(exception));
    }
  }

  private static List<ScenarioRef> refsFromJar(String classpathRoot, URL root) {
    String prefix = classpathRoot.startsWith("/") ? classpathRoot.substring(1) : classpathRoot;
    Pattern ownerEntry = Pattern.compile("^" + Pattern.quote(prefix) + "/([^/]+)\\."
        + Pattern.quote(WORKFLOW_SUFFIX.substring(1)) + "/" + VERIFICATION_SUBDIR + "/([^/]+)/"
        + Pattern.quote(EXPECTED_RESULT_FILE) + "$");
    try {
      JarURLConnection connection = (JarURLConnection) root.openConnection();
      connection.setUseCaches(true);
      // The JarFile is owned by the URL connection cache; it is deliberately not closed here.
      JarFile jar = connection.getJarFile();
      List<ScenarioRef> refs = new ArrayList<>();
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        Matcher matcher = ownerEntry.matcher(entries.nextElement().getName());
        if (matcher.matches()) {
          refs.add(new ScenarioRef(matcher.group(1), matcher.group(2)));
        }
      }
      return refs;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate shipped-workflows catalog jar at " + root, exception);
    }
  }

  private static ScenarioCase load(String classpathRoot, ScenarioRef ref) {
    String base = verificationPath(classpathRoot, ref);
    String displayName = ref.workflowId() + "/" + ref.scenarioName();
    String scriptJson = readResource(base + SCRIPT_FILE, displayName);
    String expectedResultJson = readResource(base + EXPECTED_RESULT_FILE, displayName);
    ExpectedResult expected;
    try {
      expected = MAPPER.readValue(expectedResultJson, ExpectedResult.class);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to parse " + base + EXPECTED_RESULT_FILE, exception);
    }
    boolean readme = CatalogScenarios.class.getResource(base + README_FILE) != null;
    return new ScenarioCase(displayName, ref.workflowId(), scriptJson, expectedResultJson, expected,
        readme);
  }

  private static String verificationPath(String classpathRoot, ScenarioRef ref) {
    return classpathRoot + "/" + ref.workflowId() + WORKFLOW_SUFFIX + "/" + VERIFICATION_SUBDIR + "/"
        + ref.scenarioName() + "/";
  }

  private static String workflowIdOf(String folderName) {
    return folderName.substring(0, folderName.length() - WORKFLOW_SUFFIX.length());
  }

  private static String readResource(String classpathPath, String scenarioLabel) {
    try (InputStream stream = CatalogScenarios.class.getResourceAsStream(classpathPath)) {
      if (stream == null) {
        throw new IllegalStateException(
            "Scenario '%s' is missing its verification resource: %s"
                .formatted(scenarioLabel, classpathPath));
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
    if (expect.contextPresent() != null) {
      expect.contextPresent().forEach(assertion::contextHas);
    }
    if (expect.contextMatches() != null) {
      expect.contextMatches().forEach(assertion::contextMatchesRegex);
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
    if (expect.stepVisitCounts() != null) {
      expect.stepVisitCounts().forEach((stepId, count) -> {
        if (count == null) {
          throw new AssertionError(
              "expect.stepVisitCounts value for step '%s' must not be null".formatted(stepId));
        }
        assertion.stepVisitCount(stepId, count);
      });
    }
    if (expect.orderedSteps() != null) {
      assertion.stepsInOrderedSubsequence(expect.orderedSteps().toArray(new String[0]));
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

  /**
   * Package-visible (rather than {@code private}) solely so {@code CatalogScenariosCollectionOpTest}
   * can exercise the scenario-DSL to {@link CollectionOp} conversion directly — the scenario-owning
   * catalog is empty during the clean-slate window, so there is no shipped fixture to drive the
   * {@code collection} gate type through {@link #run(ScenarioCase)} end to end.
   */
  static GateResponse toGateResponse(ExpectedResult.GateSpec spec) {
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
      case "collection" -> GateResponse.collection(toCollectionOps(spec.ops()));
      default -> throw new IllegalArgumentException("Unknown gate type: " + spec.type());
    };
  }

  private static List<CollectionOp> toCollectionOps(List<ExpectedResult.GateSpec.CollectionOpSpec> specs) {
    if (specs == null) {
      return List.of();
    }
    List<CollectionOp> ops = new ArrayList<>();
    for (ExpectedResult.GateSpec.CollectionOpSpec spec : specs) {
      ops.add(switch (spec.op()) {
        case "submit" -> new CollectionOp.Submit(spec.payloadRef(), spec.clientToken(),
            spec.dedupeKey(), spec.actorId());
        case "replace" -> new CollectionOp.Replace(ordinal(spec), spec.payloadRef(), spec.actorId());
        case "withdraw" -> new CollectionOp.Withdraw(ordinal(spec), spec.actorId());
        case "close" ->
            new CollectionOp.Close(closeReason(spec), Boolean.TRUE.equals(spec.override()));
        default -> throw new IllegalArgumentException("Unknown collection op: " + spec.op());
      });
    }
    return ops;
  }

  private static int ordinal(ExpectedResult.GateSpec.CollectionOpSpec spec) {
    if (spec.submissionId() == null) {
      throw new IllegalArgumentException(
          "collection '%s' op requires submissionId (the 0-based submit ordinal)".formatted(spec.op()));
    }
    return spec.submissionId();
  }

  private static CloseReason closeReason(ExpectedResult.GateSpec.CollectionOpSpec spec) {
    if (spec.reason() == null) {
      throw new IllegalArgumentException("collection 'close' op requires a reason");
    }
    return CloseReason.valueOf(spec.reason());
  }
}
