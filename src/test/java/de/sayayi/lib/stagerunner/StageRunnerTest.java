/*
 * Copyright 2024 Jeroen Gremmen
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
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spi.AbstractStageRunner;
import de.sayayi.lib.stagerunner.spi.AbstractStageRunnerFactory;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.*;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the execution of stage runners created by an {@link de.sayayi.lib.stagerunner.spi.AbstractStageRunnerFactory}.
 * <p>
 * Covered aspects are the order of the callback notifications, the current, processed and remaining stages and the data
 * reported by the {@link StageContext}, and stage functions added dynamically during an execution.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@DisplayName("Stage runner")
class StageRunnerTest
{
  @Test
  @DisplayName("No stage functions")
  void noStageFunctions()
  {
    val factory = new MyStageRunnerFactory();
    val runner = factory.createRunner();

    assertTrue(runner.run(emptyMap()));
    assertEquals(emptyList(), runner.log);
  }


  @Test
  @DisplayName("Pre/post callback hooks")
  void callbackHooks()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx -> {});
    factory.addStageFunction(START, ctx -> {});
    factory.addStageFunction(END, ctx -> {});
    factory.addStageFunction(PROCESS, ctx -> {});

    val runner = factory.createRunner();

    assertTrue(runner.run(emptyMap()));
    assertEquals(
        asList(">START", ">fn", "<fn", "<START", ">PROCESS", ">fn", "<fn", ">fn", "<fn", "<PROCESS", ">END", ">fn", "<fn", "<END"),
        runner.log);
  }


  @Test
  @DisplayName("Current stage reported by stage context")
  void currentStage()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx -> assertEquals(PROCESS, ctx.getCurrentStage()));
    factory.addStageFunction(START, ctx -> assertEquals(START, ctx.getCurrentStage()));
    factory.addStageFunction(END, ctx -> assertEquals(END, ctx.getCurrentStage()));
    factory.addStageFunction(PROCESS, ctx -> assertEquals(PROCESS, ctx.getCurrentStage()));

    factory
        .createRunner()
        .run(emptyMap());
  }


  @Test
  @DisplayName("Processed stages reported by stage context")
  void processedStages()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(START), ctx.getProcessedStages()));
    factory.addStageFunction(START, ctx ->
        assertEquals(emptySet(), ctx.getProcessedStages()));
    factory.addStageFunction(END, ctx ->
        assertEquals(EnumSet.of(START, PROCESS), ctx.getProcessedStages()));
    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(START), ctx.getProcessedStages()));

    factory
        .createRunner()
        .run(emptyMap());
  }


  @Test
  @DisplayName("Remaining stages reported by stage context")
  void remainingStages()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(END), ctx.getRemainingStages()));
    factory.addStageFunction(START, ctx ->
        assertEquals(EnumSet.of(PROCESS, END), ctx.getRemainingStages()));
    factory.addStageFunction(END, ctx ->
        assertEquals(emptySet(), ctx.getRemainingStages()));
    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(END), ctx.getRemainingStages()));

    factory
        .createRunner()
        .run(emptyMap());
  }


  @Test
  @DisplayName("Data reported by stage context")
  void data()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, ctx -> assertEquals(456, ctx.<Integer>getData("A")));
    factory.addStageFunction(END, ctx -> assertEquals(Boolean.FALSE, ctx.<Boolean>getData("B")));

    val data = new HashMap<String,Object>();
    data.put("A", 456);
    data.put("B", false);

    factory
        .createRunner()
        .run(data);
  }


  @Test
  @DisplayName("Add stage function dynamically")
  void addFunctionDynamically()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, "S", ctx ->
        ctx.addStageFunction(PROCESS, "P", c -> {}));
    factory.addStageFunction(END, "E", ctx -> {});

    val runner = factory.createRunner();

    assertTrue(runner.run(emptyMap()));
    assertEquals(
        asList(">START", ">fn(S)", "<fn", "<START", ">PROCESS", ">fn(P)", "<fn", "<PROCESS", ">END", ">fn(E)", "<fn", "<END"),
        runner.log);
  }


  @Test
  @DisplayName("Rejected stage function is not added")
  void rejectedFunctionIsNotAdded()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();

    factory.addStageFunction(START, "S", ctx -> {});
    factory.addStageFunction(PROCESS, "P", ctx -> {
      assertThrows(StageRunnerConfigurationException.class,
          () -> ctx.addStageFunction(START, "late", c -> fail()));
      assertEquals(PROCESS, ctx.getCurrentStage());
      functions.addAll(ctx.getFunctions());
    });
    factory.addStageFunction(END, "E", ctx -> {});

    assertFalse(factory.createRunner().run(emptyMap()));
    assertEquals(asList("S:PROCESSED", "P:EXECUTING", "E:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Function failing with a rejected stage function is marked as failed")
  void rejectedFunctionMarksCurrentFunctionFailed()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();

    factory.addStageFunction(START, "S", ctx -> ctx.addStageFunction(INIT, "late", c -> {}));
    factory.addStageFunction(END, "E", ctx -> {});

    assertThrows(StageRunnerConfigurationException.class,
        () -> factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
          @Override
          public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
            functions.addAll(stageContext.getFunctions());
          }
        }));
    assertEquals(asList("S:FAILED", "E:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Rejected named stage function prevents enabling any accepted function")
  void rejectedNamedFunctionEnablesNone()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();

    factory.namedStageFunction("early", INIT, "early", ctx -> fail());
    factory.namedStageFunction("later", END, "later", ctx -> fail());
    factory.addStageFunction(START, "S", ctx -> {
      assertThrows(StageRunnerConfigurationException.class, () -> ctx.enableNamedStageFunctions(name -> true));
      assertEquals(START, ctx.getCurrentStage());
      functions.addAll(ctx.getFunctions());
    });
    factory.addStageFunction(PROCESS, "P", ctx -> {});

    assertFalse(factory.createRunner().run(emptyMap()));
    assertEquals(asList("S:EXECUTING", "P:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Stage functions reported by stage context are unmodifiable")
  void functionsAreUnmodifiable()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, "S", ctx -> {
      val functions = ctx.getFunctions();

      assertThrows(UnsupportedOperationException.class, () -> functions.set(0, functions.getFirst()));
      assertThrows(UnsupportedOperationException.class, functions::removeFirst);
    });

    assertTrue(factory.createRunner().run(emptyMap()));
  }


  @Test
  @DisplayName("Stage of a failed function is neither completed nor processed")
  void failedFunctionDoesNotCompleteStage()
  {
    val factory = new MyStageRunnerFactory();
    val context = new ArrayList<StageContext<TestStage>>();

    factory.addStageFunction(START, ctx -> {});
    factory.addStageFunction(PROCESS, ctx -> {
      context.add(ctx);
      throw new IllegalStateException("boom");
    });
    factory.addStageFunction(END, ctx -> fail());

    val runner = factory.createRunner();

    assertThrows(StageRunnerException.class, () -> runner.run(emptyMap()));
    assertEquals(asList(">START", ">fn", "<fn", "<START", ">PROCESS", ">fn", "<fn"), runner.log);
    assertEquals(singleton(START), context.getFirst().getProcessedStages());
  }


  private static List<String> states(List<StageContext.Function<TestStage>> functions)
  {
    val states = new ArrayList<String>();

    for(val function: functions)
      states.add(function.getDescription() + ':' + function.getFunctionState());

    return states;
  }




  private static class MyStageRunnerFactory extends AbstractStageRunnerFactory<TestStage>
  {
    private MyStageRunnerFactory() {
      super(TestStage.class);
    }


    @Override
    public @NotNull MyStageRunner createRunner() {
      return new MyStageRunner(MyStageRunnerFactory.this);
    }
  }




  private static class MyStageRunner
      extends AbstractStageRunner<TestStage>
      implements StageRunnerCallback<TestStage>
  {
    private final List<String> log = new ArrayList<>();


    private MyStageRunner(@NotNull AbstractStageRunnerFactory<TestStage> stageRunnerFactory) {
      super(stageRunnerFactory);
    }


    @Override
    public boolean run(@NotNull Map<String, Object> data) {
      return super.run(data, this);
    }


    @Override
    public void preStageCallback(@NotNull StageContext<TestStage> stageContext) {
      log.add(">" + stageContext.getCurrentStage());
    }


    @Override
    public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage) {
      log.add("<" + stage);
    }

    @Override
    public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description) {
      log.add(">fn" + (description == null ? "" : "(" + description + ')'));
    }


    @Override
    public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
      log.add("<fn");
    }


    @Override
    @SneakyThrows
    public void stageExceptionHandler(@NotNull StageContext<TestStage> stageContext, @NotNull Throwable exception)
    {
      if (exception instanceof AssertionFailedError)
        throw exception;

      StageRunnerCallback.super.stageExceptionHandler(stageContext, exception);
    }
  }
}
