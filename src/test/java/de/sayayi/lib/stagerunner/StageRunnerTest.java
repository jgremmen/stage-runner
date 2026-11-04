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
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the execution of stage runners created by an {@link de.sayayi.lib.stagerunner.spi.AbstractStageRunnerFactory}.
 * <p>
 * Covered aspects are:
 * <ul>
 *   <li>running without stage functions and the typed default callback</li>
 *   <li>the order of the callback notifications</li>
 *   <li>the current, processed and remaining stages and the data reported by the {@link StageContext}</li>
 *   <li>
 *     stage functions added dynamically during an execution, including the rejection of functions positioned before
 *     the current stage function and of named stage functions which cannot be enabled
 *   </li>
 *   <li>the function states reported after a rejected stage function or a failing callback</li>
 * </ul>
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
  @DisplayName("Typed default callback")
  void typedDefaultCallback()
  {
    final StageRunnerCallback<TestStage> callback = StageRunnerCallback.defaultCallback();

    assertSame(callback, StageRunnerCallback.<TestStage>defaultCallback());

    val factory = new MyStageRunnerFactory();
    factory.addStageFunction(INIT, ctx -> { throw new IllegalStateException("boom"); });

    val ex = assertThrows(StageRunnerException.class, () -> factory.createRunner().run(emptyMap(), callback));
    assertInstanceOf(IllegalStateException.class, ex.getCause());
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
  @DisplayName("Stage function added in front of the first function of a stage in preStageCallback is executed first")
  void addInFrontOfFirstFunctionInPreStageCallback()
  {
    val factory = new MyStageRunnerFactory();
    val log = new ArrayList<String>();

    factory.addStageFunction(START, "S", ctx -> log.add("S"));
    factory.addStageFunction(PROCESS, 10, "P", ctx -> log.add("P"));

    assertTrue(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void preStageCallback(@NotNull StageContext<TestStage> stageContext)
      {
        if (stageContext.getCurrentStage() == PROCESS)
          stageContext.addStageFunction(PROCESS, 5, "early", ctx -> log.add("early"));
      }

      @Override
      public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description) {
        log.add(">" + description);
      }
    }));

    assertEquals(asList(">S", "S", ">early", "early", ">P", "P"), log);
  }


  @Test
  @DisplayName("Stage function added in front of the next function in preStageFunctionCallback is rejected")
  void addInFrontOfNextFunctionInPreStageFunctionCallbackIsRejected()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();
    val exceptions = new ArrayList<StageRunnerConfigurationException>();

    factory.addStageFunction(START, "S", ctx -> {});
    factory.addStageFunction(PROCESS, 10, "P", ctx -> fail());

    assertFalse(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description)
      {
        if ("P".equals(description))
        {
          exceptions.add(assertThrows(StageRunnerConfigurationException.class,
              () -> stageContext.addStageFunction(PROCESS, 5, "early", ctx -> fail())));
          functions.addAll(stageContext.getFunctions());
        }
      }
    }));

    assertEquals(
        "stage function for stage PROCESS and order 5 cannot be positioned in front of the current stage function; " +
        "stage functions preceding it must be added in preStageCallback at the latest",
        exceptions.getFirst().getMessage());
    assertEquals(asList("S:PROCESSED", "P:EXECUTING"), states(functions));
  }


  @Test
  @DisplayName("Stage function of a skipped stage added in preStageCallback is rejected")
  void addToSkippedStageInPreStageCallbackIsRejected()
  {
    val factory = new MyStageRunnerFactory();
    val exceptions = new ArrayList<StageRunnerConfigurationException>();

    factory.addStageFunction(START, "S", ctx -> {});
    factory.addStageFunction(END, "E", ctx -> fail());

    assertFalse(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void preStageCallback(@NotNull StageContext<TestStage> stageContext)
      {
        // PROCESS has no functions and has been skipped; a function for it would precede the first function of END
        if (stageContext.getCurrentStage() == END)
        {
          exceptions.add(assertThrows(StageRunnerConfigurationException.class,
              () -> stageContext.addStageFunction(PROCESS, ctx -> fail())));
        }
      }
    }));

    assertEquals("stage runner has passed beyond stage PROCESS and order 1000", exceptions.getFirst().getMessage());
  }


  @Test
  @DisplayName("Function failing with a rejected stage function is marked as failed")
  void rejectedFunctionMarksCurrentFunctionFailed()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();

    factory.addStageFunction(START, "S", ctx ->
        ctx.addStageFunction(INIT, "late", c -> {}));
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
  @DisplayName("Function remains waiting if preStageFunctionCallback fails")
  void failingPreStageFunctionCallbackKeepsFunctionWaiting()
  {
    val factory = new MyStageRunnerFactory();
    val contexts = new ArrayList<StageContext<TestStage>>();
    val exception = new IllegalStateException("callback failed");

    factory.addStageFunction(START, "S", ctx -> fail());
    factory.addStageFunction(END, "E", ctx -> fail());

    assertSame(exception, assertThrows(IllegalStateException.class,
        () -> factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
          @Override
          public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description)
          {
            contexts.add(stageContext);
            throw exception;
          }
        })));
    assertEquals(asList("S:WAITING", "E:WAITING"), states(contexts.getFirst().getFunctions()));
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
  @DisplayName("Named stage functions with the same stage and order are executed in registration order")
  void namedFunctionsInRegistrationOrder()
  {
    val factory = new MyStageRunnerFactory();
    val names = asList("zeta", "alpha", "mid", "beta", "omega", "x1", "q");
    val executed = new ArrayList<String>();

    for(val name: names)
      factory.namedStageFunction(name, PROCESS, ctx -> executed.add(name));

    factory.addStageFunction(START, ctx -> ctx.enableNamedStageFunctions(name -> true));

    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(names, executed);
  }


  @Test
  @DisplayName("Stage functions reported by stage context are unmodifiable")
  @SuppressWarnings("DataFlowIssue")
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


  @Test
  @DisplayName("Error thrown by a stage function is rethrown unchanged without invoking the exception handler")
  void errorIsRethrownUnchanged()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();
    val handled = new ArrayList<Throwable>();
    val error = new StackOverflowError();

    factory.addStageFunction(START, "S", ctx -> { throw error; });
    factory.addStageFunction(END, "E", ctx -> {});

    assertSame(error, assertThrows(StackOverflowError.class,
        () -> factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
          @Override
          public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
            functions.addAll(stageContext.getFunctions());
          }

          @Override
          public void stageExceptionHandler(@NotNull StageContext<TestStage> stageContext,
                                            @NotNull Throwable exception) {
            handled.add(exception);  // swallowing handler must not be able to continue the run
          }
        })));

    assertEquals(emptyList(), handled);
    assertEquals(asList("S:FAILED", "E:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Exception from postStageFunctionCallback does not mask an error thrown by a stage function")
  void postStageFunctionCallbackDoesNotMaskError()
  {
    val factory = new MyStageRunnerFactory();
    val error = new StackOverflowError();
    val callbackException = new IllegalStateException("callback");

    factory.addStageFunction(START, ctx -> { throw error; });

    assertSame(error, assertThrows(StackOverflowError.class,
        () -> factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
          @Override
          public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
            throw callbackException;
          }
        })));

    assertArrayEquals(new Throwable[] { callbackException }, error.getSuppressed());
  }


  @Test
  @DisplayName("Exception from postStageFunctionCallback does not mask the exception of the exception handler")
  void postStageFunctionCallbackDoesNotMaskHandlerException()
  {
    val factory = new MyStageRunnerFactory();
    val failure = new IllegalArgumentException("boom");
    val callbackException = new IllegalStateException("callback");

    factory.addStageFunction(START, ctx -> { throw failure; });

    val ex = assertThrows(StageRunnerException.class,
        () -> factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
          @Override
          public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
            throw callbackException;
          }
        }));

    assertSame(failure, ex.getCause());
    assertArrayEquals(new Throwable[] { callbackException }, ex.getSuppressed());
  }


  @Test
  @DisplayName("Exception from postStageFunctionCallback propagates if the stage function succeeded")
  void postStageFunctionCallbackExceptionPropagates()
  {
    val factory = new MyStageRunnerFactory();
    val callbackException = new IllegalStateException("callback");

    factory.addStageFunction(START, ctx -> {});

    assertSame(callbackException, assertThrows(IllegalStateException.class,
        () -> factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
          @Override
          public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
            throw callbackException;
          }
        })));
  }


  @Test
  @DisplayName("Stage context reports the completed stage within postStageCallback")
  void contextWithinPostStageCallback()
  {
    val factory = new MyStageRunnerFactory();
    val log = new ArrayList<String>();

    factory.addStageFunction(START, ctx -> log.add("fn START"));
    factory.addStageFunction(PROCESS, ctx -> log.add("fn PROCESS"));

    assertTrue(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage)
      {
        log.add("<" + stage + " current=" + stageContext.getCurrentStage() +
            " processed=" + stageContext.getProcessedStages() + " remaining=" + stageContext.getRemainingStages());

        // stage functions can still be added to the following stages, even after the last stage has completed
        if (stage == PROCESS)
          stageContext.addStageFunction(CLEANUP, ctx -> log.add("fn CLEANUP"));
      }
    }));

    assertEquals(asList(
        "fn START",
        "<START current=START processed=[START] remaining=[PROCESS]",
        "fn PROCESS",
        "<PROCESS current=PROCESS processed=[START, PROCESS] remaining=[]",
        "fn CLEANUP",
        "<CLEANUP current=CLEANUP processed=[START, PROCESS, CLEANUP] remaining=[]"), log);
  }


  @Test
  @DisplayName("Stage function cannot be added to a completed stage within postStageCallback")
  void addToCompletedStageIsRejected()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, ctx -> {});
    factory.addStageFunction(PROCESS, ctx -> fail());

    assertFalse(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage)
      {
        assertThrows(StageRunnerConfigurationException.class,
            () -> stageContext.addStageFunction(stage, Integer.MAX_VALUE, ctx -> fail()));
        assertTrue(stageContext.isAborted());
      }
    }));
  }


  @Test
  @DisplayName("Abort in preStageCallback prevents the first function of the stage from running")
  void abortInPreStageCallback()
  {
    val factory = new MyStageRunnerFactory();
    val log = new ArrayList<String>();
    val functions = new ArrayList<StageContext.Function<TestStage>>();

    factory.addStageFunction(START, "S", ctx -> {});
    factory.addStageFunction(PROCESS, "P", ctx -> fail());

    assertFalse(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void preStageCallback(@NotNull StageContext<TestStage> stageContext)
      {
        log.add(">" + stageContext.getCurrentStage());

        if (stageContext.getCurrentStage() == PROCESS)
        {
          stageContext.abort();
          functions.addAll(stageContext.getFunctions());
        }
      }

      @Override
      public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description) {
        log.add(">fn(" + description + ')');
      }

      @Override
      public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
        log.add("<fn");
      }
    }));

    assertEquals(asList(">START", ">fn(S)", "<fn", ">PROCESS"), log);
    assertEquals(asList("S:PROCESSED", "P:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Abort in preStageFunctionCallback skips the function")
  void abortInPreStageFunctionCallback()
  {
    val factory = new MyStageRunnerFactory();
    val log = new ArrayList<String>();
    val functions = new ArrayList<StageContext.Function<TestStage>>();

    factory.addStageFunction(START, "S", ctx -> {});
    factory.addStageFunction(START, "A", ctx -> fail());
    factory.addStageFunction(END, "E", ctx -> fail());

    assertFalse(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description)
      {
        log.add(">fn(" + description + ')');

        if ("A".equals(description))
          stageContext.abort();
      }

      @Override
      public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext)
      {
        log.add("<fn");
        functions.clear();
        functions.addAll(stageContext.getFunctions());
      }

      @Override
      public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage) {
        log.add("<" + stage);
      }
    }));

    assertEquals(asList(">fn(S)", "<fn", ">fn(A)", "<fn"), log);
    assertEquals(asList("S:PROCESSED", "A:WAITING", "E:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Stage runner can be run multiple times")
  void runnerIsReusable()
  {
    val factory = new MyStageRunnerFactory();
    val executed = new ArrayList<>();

    factory.addStageFunction(START, ctx -> executed.add(ctx.getData("run")));

    val runner = factory.createRunner();

    assertTrue(runner.run(singletonMap("run", 1)));
    assertTrue(runner.run(singletonMap("run", 2)));
    assertEquals(asList(1, 2), executed);
  }


  @Test
  @DisplayName("Abort in a stage function ends the run unsuccessfully after the function returns")
  void abortInStageFunction()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();
    val aborted = new ArrayList<Boolean>();

    factory.addStageFunction(START, "S", ctx -> {
      assertFalse(ctx.isAborted());
      ctx.abort();
      aborted.add(ctx.isAborted());
    });
    factory.addStageFunction(START, "S2", ctx -> fail());
    factory.addStageFunction(END, "E", ctx -> fail());

    val runner = factory.createRunner();

    assertFalse(runner.run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
        functions.addAll(stageContext.getFunctions());
      }

      @Override
      public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage) {
        fail("aborted stage must not be completed");
      }
    }));

    assertEquals(singletonList(true), aborted);
    assertEquals(asList("S:PROCESSED", "S2:WAITING", "E:WAITING"), states(functions));
  }


  @Test
  @DisplayName("Exception handler returning normally continues the run")
  void exceptionHandledByCallback()
  {
    val factory = new MyStageRunnerFactory();
    val functions = new ArrayList<StageContext.Function<TestStage>>();
    val handled = new ArrayList<String>();

    factory.addStageFunction(START, "S", ctx -> { throw new IllegalStateException("boom"); });
    factory.addStageFunction(END, "E", ctx -> {});

    assertTrue(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void stageExceptionHandler(@NotNull StageContext<TestStage> stageContext, @NotNull Throwable exception) {
        handled.add(stageContext.getCurrentStage() + ":" + exception.getMessage());
      }

      @Override
      public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage)
      {
        if (stage == END)
          functions.addAll(stageContext.getFunctions());
      }
    }));

    assertEquals(singletonList("START:boom"), handled);
    assertEquals(asList("S:FAILED", "E:PROCESSED"), states(functions));
  }


  @Test
  @DisplayName("Default exception handler wraps exceptions and rethrows stage runner exceptions unchanged")
  void defaultExceptionHandler()
  {
    val factory = new MyStageRunnerFactory();
    val cause = new IllegalStateException("boom");

    factory.addStageFunction(START, ctx -> { throw cause; });

    val wrapped = assertThrows(StageRunnerException.class, () -> factory.createRunner().run(emptyMap()));
    assertEquals("stage function failed in stage START: java.lang.IllegalStateException: boom", wrapped.getMessage());
    assertSame(cause, wrapped.getCause());

    val factory2 = new MyStageRunnerFactory();
    val exception = new StageRunnerException("direct");

    factory2.addStageFunction(START, ctx -> { throw exception; });

    assertSame(exception, assertThrows(StageRunnerException.class, () -> factory2.createRunner().run(emptyMap())));
  }


  @Test
  @DisplayName("Default exception handler message names the stage for an exception without message")
  void defaultExceptionHandlerWithoutMessage()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, ctx -> {});
    factory.addStageFunction(PROCESS, ctx -> { throw new UnsupportedOperationException(); });

    val wrapped = assertThrows(StageRunnerException.class, () -> factory.createRunner().run(emptyMap()));
    assertEquals("stage function failed in stage PROCESS: java.lang.UnsupportedOperationException",
        wrapped.getMessage());
  }


  @Test
  @DisplayName("Configuration exceptions are passed to the configuration exception handler")
  void configurationExceptionHandler()
  {
    val factory = new MyStageRunnerFactory();
    val handled = new ArrayList<StageRunnerConfigurationException>();
    val other = new ArrayList<Throwable>();
    val exception = new StageRunnerConfigurationException("config");

    factory.addStageFunction(START, ctx -> { throw exception; });
    factory.addStageFunction(END, ctx -> {});

    assertTrue(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void stageExceptionHandler(@NotNull StageContext<TestStage> stageContext, @NotNull Throwable ex) {
        other.add(ex);
      }

      @Override
      public void stageConfigurationExceptionHandler(@NotNull StageContext<TestStage> stageContext,
                                                     @NotNull StageRunnerConfigurationException ex) {
        handled.add(ex);
      }
    }));

    assertEquals(singletonList(exception), handled);
    assertEquals(emptyList(), other);
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
  }
}
