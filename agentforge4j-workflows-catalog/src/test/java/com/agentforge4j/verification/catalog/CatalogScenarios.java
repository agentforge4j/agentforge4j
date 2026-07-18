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

  /**
   * Lists {@code verification/} sub-folders that carry scenario content but no
   * {@code expected-result.json} discovery marker. Such a folder silently stops being a test (the
   * marker is the sole discovery trigger), so the conformance gate fails on any hit — a renamed or
   * deleted marker must not silently drop a scenario while its sibling files sit there looking
   * covered.
   *
   * @return {@code <workflowId>/<scenario>} names of marker-less scenario folders, sorted
   */
  public static List<String> unmarkedScenarioFolders() {
    return unmarkedScenarioFolders(SHIPPED_WORKFLOWS_ROOT);
  }

  static List<String> unmarkedScenarioFolders(String classpathRoot) {
    URL root = CatalogScenarios.class.getResource(classpathRoot);
    if (root == null) {
      return List.of();
    }
    List<String> unmarked = new ArrayList<>();
    switch (root.getProtocol()) {
      case "file" -> {
        for (Path workflowDir : workflowDirectories(root)) {
          String workflowId = workflowIdOf(workflowDir.getFileName().toString());
          Path verificationDir = workflowDir.resolve(VERIFICATION_SUBDIR);
          if (!Files.isDirectory(verificationDir)) {
            continue;
          }
          try (Stream<Path> scenarioDirs = Files.list(verificationDir)) {
            scenarioDirs
                .filter(Files::isDirectory)
                .filter(dir -> !Files.exists(dir.resolve(EXPECTED_RESULT_FILE)))
                .sorted()
                .forEach(dir -> unmarked.add(workflowId + "/" + dir.getFileName()));
          } catch (IOException exception) {
            throw new UncheckedIOException(
                "Failed to enumerate verification folders under " + workflowDir, exception);
          }
        }
      }
      case "jar" -> {
        String prefix = stripLeadingSlash(classpathRoot);
        Pattern scenarioEntry = Pattern.compile("^" + Pattern.quote(prefix) + "/([^/]+)"
            + Pattern.quote(WORKFLOW_SUFFIX) + "/" + VERIFICATION_SUBDIR + "/([^/]+)/.+$");
        Set<String> seen = new LinkedHashSet<>();
        Set<String> marked = new LinkedHashSet<>();
        for (String entryName : jarEntryNames(root)) {
          Matcher matcher = scenarioEntry.matcher(entryName);
          if (matcher.matches()) {
            String scenario = matcher.group(1) + "/" + matcher.group(2);
            seen.add(scenario);
            if (entryName.endsWith("/" + EXPECTED_RESULT_FILE)) {
              marked.add(scenario);
            }
          }
        }
        seen.stream().filter(scenario -> !marked.contains(scenario)).sorted()
            .forEach(unmarked::add);
      }
      default -> throw new IllegalStateException(
          "Unsupported shipped-workflows catalog URL protocol: " + root);
    }
    return unmarked;
  }

  /**
   * Enumerates the ids of every physical {@code <id>.workflow} folder under the catalog root —
   * regardless of index membership or verification content. The conformance gate cross-checks this
   * against the shipped index so an unindexed folder cannot ship as silent dead cargo in the jar.
   *
   * @return ids of all physical workflow folders (empty when the catalog root is absent)
   */
  public static Set<String> physicalWorkflowFolderIds() {
    return physicalWorkflowFolderIds(SHIPPED_WORKFLOWS_ROOT);
  }

  static Set<String> physicalWorkflowFolderIds(String classpathRoot) {
    URL root = CatalogScenarios.class.getResource(classpathRoot);
    if (root == null) {
      return Set.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    switch (root.getProtocol()) {
      case "file" -> {
        for (Path workflowDir : workflowDirectories(root)) {
          ids.add(workflowIdOf(workflowDir.getFileName().toString()));
        }
      }
      case "jar" -> {
        String prefix = stripLeadingSlash(classpathRoot);
        Pattern workflowEntry = Pattern.compile("^" + Pattern.quote(prefix) + "/([^/]+)"
            + Pattern.quote(WORKFLOW_SUFFIX) + "/.+$");
        for (String entryName : jarEntryNames(root)) {
          Matcher matcher = workflowEntry.matcher(entryName);
          if (matcher.matches()) {
            ids.add(matcher.group(1));
          }
        }
      }
      default -> throw new IllegalStateException(
          "Unsupported shipped-workflows catalog URL protocol: " + root);
    }
    return ids;
  }

  /**
   * Lists catalog-root directory entries that are neither {@code <id>.workflow} folders nor the two
   * known root files ({@code index}, the compatibility manifest). A directory whose name lacks the
   * {@code .workflow} suffix is invisible to both discovery and the production loader, so the
   * conformance gate fails on any hit rather than letting it ship unnoticed.
   *
   * @return names of unexpected catalog-root entries, sorted (empty when the root is absent or a
   *         jar — jar layouts have no stray-entry surface of their own; the exploded module source
   *         is the authoritative check site)
   */
  public static List<String> strayCatalogRootEntries() {
    return strayCatalogRootEntries(SHIPPED_WORKFLOWS_ROOT);
  }

  static List<String> strayCatalogRootEntries(String classpathRoot) {
    URL root = CatalogScenarios.class.getResource(classpathRoot);
    if (root == null || !"file".equals(root.getProtocol())) {
      return List.of();
    }
    try {
      Path rootDir = Path.of(root.toURI());
      try (Stream<Path> entries = Files.list(rootDir)) {
        return entries
            .filter(entry -> !(Files.isDirectory(entry)
                && entry.getFileName().toString().endsWith(WORKFLOW_SUFFIX)))
            .map(entry -> entry.getFileName().toString())
            .filter(name -> !"index".equals(name) && !"agentforge4j-catalog.json".equals(name))
            .sorted()
            .toList();
      }
    } catch (URISyntaxException | IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate catalog root at " + root, new IOException(exception));
    }
  }

  private static List<Path> workflowDirectories(URL fileRoot) {
    try {
      Path rootDir = Path.of(fileRoot.toURI());
      try (Stream<Path> entries = Files.list(rootDir)) {
        return entries
            .filter(Files::isDirectory)
            .filter(dir -> dir.getFileName().toString().endsWith(WORKFLOW_SUFFIX))
            .sorted()
            .toList();
      }
    } catch (URISyntaxException | IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate shipped-workflows catalog at " + fileRoot,
          new IOException(exception));
    }
  }

  private static List<String> jarEntryNames(URL jarRoot) {
    try {
      JarURLConnection connection = (JarURLConnection) jarRoot.openConnection();
      connection.setUseCaches(true);
      // The JarFile is owned by the URL connection cache; it is deliberately not closed here.
      JarFile jar = connection.getJarFile();
      List<String> names = new ArrayList<>();
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        names.add(entries.nextElement().getName());
      }
      return names;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to enumerate shipped-workflows catalog jar at " + jarRoot, exception);
    }
  }

  private static String stripLeadingSlash(String classpathRoot) {
    return classpathRoot.startsWith("/") ? classpathRoot.substring(1) : classpathRoot;
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
    if (expect.notEmittedEvents() != null) {
      expect.notEmittedEvents()
          .forEach(name -> assertion.didNotEmitEvent(WorkflowEventType.valueOf(name)));
    }
    if (expect.createdFiles() != null) {
      expect.createdFiles().forEach(assertion::createdFile);
    }
    if (expect.absentFiles() != null) {
      expect.absentFiles().forEach(assertion::artifactAbsent);
    }
    if (expect.failedBecause() != null) {
      assertion.failedBecause(expect.failedBecause());
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

  private static GateResponse toGateResponse(ExpectedResult.GateSpec spec) {
    String toolId = spec.toolInvocationId();
    return switch (spec.type()) {
      case "input" -> GateResponse.input(spec.answers() == null ? Map.of() : spec.answers());
      case "review" -> GateResponse.review(spec.note());
      case "stepApproval" -> {
        if (spec.approve() == null) {
          throw new IllegalArgumentException(
              "A stepApproval gate must state approve explicitly; an omitted flag must not "
                  + "silently become a rejection");
        }
        yield spec.approve()
            ? GateResponse.approveStep(spec.note())
            : GateResponse.rejectStep(spec.note());
      }
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
