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
import de.sayayi.lib.stagerunner.TestStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the generated stage runner proxy.
 * <p>
 * Covered aspects are passing boxed primitive arguments of a {@code void} interface method to instance and static
 * stage functions, and the result of a {@code boolean} interface method for an aborted run.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage runner proxy")
class StageRunnerProxyTest
{
  @Test
  @DisplayName("Void method passes boxed primitive arguments to instance and static stage functions")
  void voidMethodWithPrimitives()
  {
    try(var context = new AnnotationConfigApplicationContext(PrimitiveConfiguration.class)) {
      final var log = context.getBean(PrimitiveBean.class).log;

      context.getBean(PrimitiveRunner.class).run(1234567890123L, 2.5, true, 'x', (byte)7, "text");

      assertEquals(List.of(1234567890123L, 2.5, true, 'x', (byte)7, "text"), log);
    }
  }


  @Test
  @DisplayName("Boolean method returns false if the run has been aborted")
  void booleanResult()
  {
    try(var context = new AnnotationConfigApplicationContext(AbortConfiguration.class)) {
      final var runner = context.getBean(AbortRunner.class);

      assertTrue(runner.run(false));
      assertFalse(runner.run(true));
    }
  }




  @FunctionalInterface
  interface PrimitiveRunner {
    void run(long id, double ratio, boolean flag, char letter, byte small, String text);
  }




  @FunctionalInterface
  interface AbortRunner {
    boolean run(boolean abort);
  }




  @SuppressWarnings("unused")
  static class PrimitiveBean
  {
    // written by the static stage function as well
    static final List<Object> STATIC_LOG = new CopyOnWriteArrayList<>();

    final List<Object> log = STATIC_LOG;


    PrimitiveBean() {
      STATIC_LOG.clear();
    }


    @StageDef(stage = TestStage.INIT, comment = "Static")
    public static void staticFunction(long id, double ratio)
    {
      STATIC_LOG.add(id);
      STATIC_LOG.add(ratio);
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Instance")
    public void instanceFunction(boolean flag, char letter, byte small, String text)
    {
      log.add(flag);
      log.add(letter);
      log.add(small);
      log.add(text);
    }
  }




  @SuppressWarnings("unused")
  static class AbortBean
  {
    @StageDef(stage = TestStage.INIT, comment = "Abort")
    public void abort(StageContext<TestStage> context, boolean abort)
    {
      if (abort)
        context.abort();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class PrimitiveConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<PrimitiveRunner> processor() {
      return new StageRunnerFactoryProcessor<>(PrimitiveRunner.class, StageDef.class);
    }


    @Bean
    PrimitiveBean primitiveBean() {
      return new PrimitiveBean();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class AbortConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<AbortRunner> processor() {
      return new StageRunnerFactoryProcessor<>(AbortRunner.class, StageDef.class);
    }


    @Bean
    AbortBean abortBean() {
      return new AbortBean();
    }
  }
}
