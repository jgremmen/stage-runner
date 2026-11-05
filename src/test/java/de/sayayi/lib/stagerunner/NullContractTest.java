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

import de.sayayi.lib.stagerunner.spi.AbstractStageRunner;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests that {@code null} arguments are rejected where they are passed to the stage runner API.
 * <p>
 * Covered are the stage runner factory (stage enum type, regular and named stage functions), the stage context (adding
 * stage functions, data access and enabling named stage functions) and the stage runner (factory, data map and
 * callback).
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Null contracts")
@SuppressWarnings("DataFlowIssue")
class NullContractTest
{
  @Test
  @DisplayName("Factory rejects a null stage enum type")
  void nullStageEnumType() {
    assertNullRejected("stageEnumType", () -> new DefaultStageRunnerFactory<TestStage>(null));
  }


  @Test
  @DisplayName("Factory rejects a null stage or function")
  void factoryAddStageFunction()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);

    assertNullRejected("stage", () -> factory.addStageFunction(null, ctx -> {}));
    assertNullRejected("function", () -> factory.addStageFunction(START, null));

    // a rejected function must leave the factory usable
    factory.addStageFunction(START, ctx -> {});
    assertTrue(factory.createRunner().run(emptyMap()));
  }


  @Test
  @DisplayName("Factory rejects a null stage or function for a named stage function")
  void factoryNamedStageFunction()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);

    assertNullRejected("stage", () -> factory.namedStageFunction("a", null, ctx -> {}));
    assertNullRejected("function", () -> factory.namedStageFunction("b", START, null));

    // a rejected function must not occupy its name
    factory.namedStageFunction("a", START, ctx -> {});
    factory.namedStageFunction("b", START, ctx -> {});
  }


  @Test
  @DisplayName("Stage context rejects a null stage or function")
  void contextAddStageFunction()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.addStageFunction(INIT, ctx -> {
      assertNullRejected("stage", () -> ctx.addStageFunction(null, c -> {}));
      assertNullRejected("function", () -> ctx.addStageFunction(END, null));
      executed.add("INIT");
    });

    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(List.of("INIT"), executed);
  }


  @Test
  @DisplayName("Stage context rejects a null data name independent of the data map")
  void contextGetData()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.addStageFunction(INIT, ctx -> {
      assertNullRejected("name", () -> ctx.getData(null));
      executed.add("INIT");
    });

    // a hash map accepts null keys, so the rejection must not depend on the data map
    assertTrue(factory.createRunner().run(new HashMap<>()));
    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(List.of("INIT", "INIT"), executed);
  }


  @Test
  @DisplayName("Stage context rejects a null name or name filter, even without named stage functions")
  void contextEnableNamedStageFunctions()
  {
    var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    var executed = new ArrayList<String>();

    factory.addStageFunction(INIT, ctx -> {
      assertNullRejected("name", () -> ctx.enableNamedStageFunction(null));
      assertNullRejected("nameFilter", () -> ctx.enableNamedStageFunctions(null));
      executed.add("INIT");
    });

    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(List.of("INIT"), executed);
  }


  @Test
  @DisplayName("Stage runner rejects a null factory, data map or callback")
  void runner()
  {
    var runner = new DefaultStageRunnerFactory<>(TestStage.class).createRunner();

    assertNullRejected("stageRunnerFactory", () -> new AbstractStageRunner<TestStage>(null) {});
    assertNullRejected("data", () -> runner.run(null));
    assertNullRejected("callback", () -> runner.run(emptyMap(), null));
  }


  private static void assertNullRejected(String name, Executable executable) {
    assertEquals(name + " must not be null", assertThrows(NullPointerException.class, executable).getMessage());
  }
}
