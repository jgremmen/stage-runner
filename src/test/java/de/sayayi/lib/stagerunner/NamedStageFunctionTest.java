/*
 * Copyright 2026 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.stagerunner;

import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the registration and enabling of named stage functions.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Named stage functions")
class NamedStageFunctionTest
{
  @Test
  @DisplayName("Named stage functions are only executed if enabled")
  void onlyEnabledFunctionsAreExecuted()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.namedStageFunction("a", PROCESS, ctx -> executed.add("a"));
    factory.namedStageFunction("b", PROCESS, ctx -> executed.add("b"));
    factory.addStageFunction(START, ctx -> assertTrue(ctx.enableNamedStageFunction("b")));

    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(List.of("b"), executed);
  }


  @Test
  @DisplayName("Enabling an unknown or already enabled stage function has no effect")
  void enableUnknownOrEnabledFunction()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.namedStageFunction("a", PROCESS, ctx -> executed.add("a"));
    factory.addStageFunction(START, ctx -> {
      assertFalse(ctx.enableNamedStageFunction("unknown"));
      assertTrue(ctx.enableNamedStageFunction("a"));
      assertFalse(ctx.enableNamedStageFunction("a"));
      assertEquals(Set.of(), ctx.enableNamedStageFunctions(name -> true));
    });

    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(List.of("a"), executed);
  }


  @Test
  @DisplayName("Enabling by predicate returns the enabled names")
  void enableByPredicate()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.namedStageFunction("metrics.a", PROCESS, ctx -> executed.add("metrics.a"));
    factory.namedStageFunction("metrics.b", END, ctx -> executed.add("metrics.b"));
    factory.namedStageFunction("other", PROCESS, ctx -> executed.add("other"));
    factory.addStageFunction(START, ctx ->
        assertEquals(Set.of("metrics.a", "metrics.b"), ctx.enableNamedStageFunctions(n -> n.startsWith("metrics."))));

    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(List.of("metrics.a", "metrics.b"), executed);
  }


  @Test
  @DisplayName("Enabled stage functions only apply to the current execution")
  void enabledPerExecution()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.namedStageFunction("a", PROCESS, ctx -> executed.add("a"));
    factory.addStageFunction(START, ctx -> {
      if (ctx.<Boolean>getData("enable"))
        ctx.enableNamedStageFunction("a");
    });

    var runner = factory.createRunner();

    assertTrue(runner.run(Map.of("enable", true)));
    assertTrue(runner.run(Map.of("enable", false)));
    assertEquals(List.of("a"), executed);
  }


  @Test
  @DisplayName("Duplicate and empty names are rejected")
  void invalidNames()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);

    factory.namedStageFunction("a", PROCESS, ctx -> {});

    assertEquals("name 'a' must be unique for this stage runner factory",
        assertThrows(StageRunnerConfigurationException.class,
            () -> factory.namedStageFunction("a", END, ctx -> {})).getMessage());
    assertEquals("name must not be empty",
        assertThrows(StageRunnerConfigurationException.class,
            () -> factory.namedStageFunction("", END, ctx -> {})).getMessage());
  }
}
