// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticFakeResponseSourceTest {

  private static final FakeScriptKey KEY_0 = new FakeScriptKey("wf", "s1", "a1", 0);
  private static final FakeScriptKey KEY_1 = new FakeScriptKey("wf", "s1", "a1", 1);

  private static FakeInvocation invocation(String runId) {
    return new FakeInvocation("wf", runId, "s1", "a1");
  }

  @Test
  void servesSingleScript_forAnyRun_neverRunNotScripted() {
    StaticFakeResponseSource source = new StaticFakeResponseSource(new FakeScript(1, Map.of(
        KEY_0, new FakeResponse("hello", null))));

    FakeResolution first = source.nextResponse(invocation("any-run"));
    FakeResolution second = source.nextResponse(invocation("another-run"));

    assertThat(first).isInstanceOf(FakeResolution.Found.class);
    assertThat(second).isInstanceOf(FakeResolution.Found.class);
    assertThat(((FakeResolution.Found) first).response().responseText()).isEqualTo("hello");
  }

  @Test
  void counters_areRunScoped_soRepeatedRunsDoNotShareAnEverClimbingCounter() {
    StaticFakeResponseSource source = new StaticFakeResponseSource(new FakeScript(1, Map.of(
        KEY_0, new FakeResponse("zero", null), KEY_1, new FakeResponse("one", null))));

    // run-a consumes ordinal 0 then 1
    assertThat(found(source, "run-a")).isEqualTo("zero");
    assertThat(found(source, "run-a")).isEqualTo("one");

    // run-b starts its OWN sequence at ordinal 0 (not continuing run-a's counter)
    assertThat(found(source, "run-b")).isEqualTo("zero");
  }

  @Test
  void missingOrdinal_failsClosed_asKeyAbsent() {
    StaticFakeResponseSource source = new StaticFakeResponseSource(new FakeScript(1, Map.of(
        KEY_0, new FakeResponse("zero", null))));

    source.nextResponse(invocation("run-a")); // ordinal 0
    FakeResolution miss = source.nextResponse(invocation("run-a")); // ordinal 1 absent

    assertThat(miss).isInstanceOf(FakeResolution.KeyAbsent.class);
  }

  private static String found(StaticFakeResponseSource source, String runId) {
    FakeResolution resolution = source.nextResponse(invocation(runId));
    assertThat(resolution).isInstanceOf(FakeResolution.Found.class);
    return ((FakeResolution.Found) resolution).response().responseText();
  }
}
