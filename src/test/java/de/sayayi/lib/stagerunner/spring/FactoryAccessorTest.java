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

import de.sayayi.lib.stagerunner.TestStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests {@link FactoryAccessor}, implemented by the stage runner proxy.
 * <p>
 * Covered aspects are accessing the factory behind the proxy, registering regular and named stage functions through
 * the accessor after discovery, and rejecting access to the configurers of a non-configurable factory.
 *
 * @author Jeroen Gremmen
 * @since 0.4.1
 */
@DisplayName("Factory accessor")
class FactoryAccessorTest
{
  @Test
  @DisplayName("Stage functions registered through the accessor are executed with the discovered ones")
  @SuppressWarnings("unchecked")
  void registerStageFunctions()
  {
    try(var context = new AnnotationConfigApplicationContext(AccessorConfiguration.class)) {
      var runner = context.getBean(AccessorRunner.class);
      var log = context.getBean(Log.class);

      assertInstanceOf(FactoryAccessor.class, runner);

      var accessor = (FactoryAccessor<TestStage>)runner;
      var factory = accessor.getStageRunnerFactory();

      assertSame(factory, accessor.getStageFunctionConfigurer());
      assertSame(factory, accessor.getNamedStageFunctionConfigurer());

      accessor.getStageFunctionConfigurer()
          .addStageFunction(END, ctx -> log.entries.add("added"));
      accessor.getNamedStageFunctionConfigurer()
          .namedStageFunction("extra", PROCESS, ctx -> log.entries.add("named"));
      accessor.getStageFunctionConfigurer()
          .addStageFunction(START, ctx -> log.entries.add("enabled:" + ctx.enableNamedStageFunction("extra")));

      runner.run();

      assertEquals(List.of("bean", "enabled:true", "named", "added"), log.entries);
    }
  }


  @Test
  @DisplayName("Configurers of a non-configurable factory are rejected")
  void nonConfigurableFactory()
  {
    FactoryAccessor<TestStage> accessor = () -> () -> fail("runner must not be created");

    assertNotNull(accessor.getStageRunnerFactory());
    assertThrows(IllegalStateException.class, accessor::getStageFunctionConfigurer);
    assertThrows(IllegalStateException.class, accessor::getNamedStageFunctionConfigurer);
  }




  @FunctionalInterface
  interface AccessorRunner {
    void run();
  }




  static class Log {
    final List<String> entries = new CopyOnWriteArrayList<>();
  }




  static class AccessorBean
  {
    private final Log log;


    AccessorBean(Log log) {
      this.log = log;
    }


    @StageDef(stage = INIT, comment = "Bean")
    public void bean() {
      log.entries.add("bean");
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class AccessorConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<AccessorRunner> processor() {
      return new StageRunnerFactoryProcessor<>(AccessorRunner.class, StageDef.class);
    }


    @Bean
    Log log() {
      return new Log();
    }


    @Bean
    AccessorBean accessorBean(Log log) {
      return new AccessorBean(log);
    }
  }
}
