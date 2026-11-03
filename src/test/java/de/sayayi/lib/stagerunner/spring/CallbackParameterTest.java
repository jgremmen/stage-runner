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
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
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




  @FunctionalInterface
  interface CallbackRunner {
    boolean run(String value, StageRunnerCallback<TestStage> callback);
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
}
