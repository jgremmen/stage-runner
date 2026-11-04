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
package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.StageRunnerParameter.CallbackParameter;
import de.sayayi.lib.stagerunner.spring.StageRunnerParameter.DataParameter;
import de.sayayi.lib.stagerunner.spring.builder.StageRunnerProxyBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests a stage runner interface method declaring a {@link StageRunnerCallback} parameter.
 * <p>
 * Covered aspects are passing the callback to the stage runner, the fallback to the default callback for {@code null},
 * raw callbacks and callback subtypes, the rejection of a callback for a different stage type and of more than one
 * callback parameter, and the validation of the parameter descriptions passed to the proxy builder.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage runner callback parameter")
class CallbackParameterTest
{
  @Test
  @DisplayName("Callback is passed to the stage runner")
  void callback()
  {
    try(var context = new AnnotationConfigApplicationContext(CallbackConfiguration.class)) {
      final var stages = new ArrayList<TestStage>();

      assertTrue(context.getBean(CallbackRunner.class).run("ok", new StageRunnerCallback<>() {
        @Override
        public void preStageCallback(@NotNull StageContext<TestStage> stageContext) {
          stages.add(stageContext.getCurrentStage());
        }
      }));

      assertEquals(List.of(TestStage.INIT), stages);
      assertEquals(List.of("ok"), context.getBean(CallbackBean.class).log);
    }
  }


  @Test
  @DisplayName("Null callback falls back to the default callback")
  void nullCallback()
  {
    try(var context = new AnnotationConfigApplicationContext(CallbackConfiguration.class)) {
      final var runner = context.getBean(CallbackRunner.class);

      assertTrue(runner.run("ok", null));
      assertEquals(List.of("ok"), context.getBean(CallbackBean.class).log);

      // the default exception handler wraps the exception
      final var exception = assertThrows(StageRunnerException.class,
          () -> runner.run("fail", null));
      assertEquals("stage function failed in stage INIT: java.lang.IllegalStateException: boom",
          exception.getMessage());
    }
  }


  @Test
  @DisplayName("Raw callback is used as callback and not passed as data")
  void rawCallback()
  {
    try(var context = new AnnotationConfigApplicationContext(RawCallbackConfiguration.class)) {
      final var stages = new ArrayList<TestStage>();

      assertTrue(context.getBean(RawCallbackRunner.class).run("ok", new StageRunnerCallback<TestStage>() {
        @Override
        public void preStageCallback(@NotNull StageContext<TestStage> stageContext) {
          stages.add(stageContext.getCurrentStage());
        }
      }));

      assertEquals(List.of(TestStage.INIT), stages);
      assertEquals(List.of("ok", "no callback data"), context.getBean(DataCheckBean.class).log);
    }
  }


  @Test
  @DisplayName("Callback subtype is used as callback")
  void callbackSubtype()
  {
    try(var context = new AnnotationConfigApplicationContext(SubtypeCallbackConfiguration.class)) {
      final var stages = new ArrayList<TestStage>();

      assertTrue(context.getBean(SubtypeCallbackRunner.class).run("ok", new TestStageCallback() {
        @Override
        public void preStageCallback(@NotNull StageContext<TestStage> stageContext) {
          stages.add(stageContext.getCurrentStage());
        }
      }));

      assertEquals(List.of(TestStage.INIT), stages);
      assertEquals(List.of("ok"), context.getBean(CallbackBean.class).log);
    }
  }


  @Test
  @DisplayName("Callback for a different stage type is rejected")
  void foreignStageTypeCallback()
  {
    final var exception = assertThrows(StageRunnerConfigurationException.class,
        () -> new StageRunnerFactoryProcessor<>(ForeignCallbackRunner.class, StageDef.class));

    assertTrue(exception.getMessage().startsWith("callback parameter 2 in stage runner function"));
  }


  @Test
  @DisplayName("More than one callback parameter is rejected")
  void duplicateCallback()
  {
    final var exception = assertThrows(StageRunnerConfigurationException.class,
        () -> new StageRunnerFactoryProcessor<>(DuplicateCallbackRunner.class, StageDef.class));

    assertTrue(exception.getMessage().startsWith("duplicate callback parameter 3 in stage runner function"));
  }


  @Test
  @DisplayName("Proxy builder rejects a callback description for a non-callback parameter")
  void proxyBuilderCallbackForNonCallbackParameter() throws Exception
  {
    final var method = CallbackRunner.class.getMethod("run", String.class, StageRunnerCallback.class);
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    final var proxyBuilder = new StageRunnerProxyBuilderImpl(false);

    final var exception = assertThrows(StageRunnerConfigurationException.class,
        () -> proxyBuilder.createProxy(TestStage.class, CallbackRunner.class, method,
            List.of(new CallbackParameter(), new CallbackParameter()), factory));

    assertTrue(exception.getMessage().startsWith("callback parameter 1 of"), exception.getMessage());
  }


  @Test
  @DisplayName("Proxy builder rejects a parameter description count which does not match the method")
  void proxyBuilderParameterCountMismatch() throws Exception
  {
    final var method = CallbackRunner.class.getMethod("run", String.class, StageRunnerCallback.class);
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    final var proxyBuilder = new StageRunnerProxyBuilderImpl(false);

    final var exception = assertThrows(StageRunnerConfigurationException.class,
        () -> proxyBuilder.createProxy(TestStage.class, CallbackRunner.class, method,
            List.of(new DataParameter("text")), factory));

    assertTrue(exception.getMessage().startsWith("expected 2 parameter descriptions"), exception.getMessage());
  }


  @Test
  @DisplayName("Data parameter rejects an empty name")
  @SuppressWarnings("DataFlowIssue")
  void dataParameterEmptyName()
  {
    assertThrows(IllegalArgumentException.class, () -> new DataParameter(""));
    assertThrows(NullPointerException.class, () -> new DataParameter(null));
  }




  @FunctionalInterface
  interface CallbackRunner {
    boolean run(String value, StageRunnerCallback<TestStage> callback);
  }




  @FunctionalInterface
  interface RawCallbackRunner
  {
    @SuppressWarnings("rawtypes")
    boolean run(String value, StageRunnerCallback callback);
  }




  interface TestStageCallback extends StageRunnerCallback<TestStage> {
  }




  @FunctionalInterface
  interface SubtypeCallbackRunner {
    boolean run(String value, TestStageCallback callback);
  }




  enum OtherStage {
    OTHER
  }




  @FunctionalInterface
  interface ForeignCallbackRunner {
    boolean run(String value, StageRunnerCallback<OtherStage> callback);
  }




  @FunctionalInterface
  interface DuplicateCallbackRunner {
    boolean run(String value, StageRunnerCallback<TestStage> callback1, StageRunnerCallback<TestStage> callback2);
  }




  static class CallbackBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Log value")
    @SuppressWarnings("unused")
    public void logValue(String value)
    {
      if ("fail".equals(value))
        throw new IllegalStateException("boom");

      log.add(value);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class CallbackConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<CallbackRunner> processor() {
      return new StageRunnerFactoryProcessor<>(CallbackRunner.class, StageDef.class);
    }


    @Bean
    CallbackBean callbackBean() {
      return new CallbackBean();
    }
  }




  static class DataCheckBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Check data")
    @SuppressWarnings("unused")
    public void checkData(String value, StageContext<TestStage> context)
    {
      log.add(value);

      if (context.getData("callback") == null)
        log.add("no callback data");
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class RawCallbackConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<RawCallbackRunner> processor() {
      return new StageRunnerFactoryProcessor<>(RawCallbackRunner.class, StageDef.class);
    }


    @Bean
    DataCheckBean dataCheckBean() {
      return new DataCheckBean();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class SubtypeCallbackConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<SubtypeCallbackRunner> processor() {
      return new StageRunnerFactoryProcessor<>(SubtypeCallbackRunner.class, StageDef.class);
    }


    @Bean
    CallbackBean callbackBean() {
      return new CallbackBean();
    }
  }
}
