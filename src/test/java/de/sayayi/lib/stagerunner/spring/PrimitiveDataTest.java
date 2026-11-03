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
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests stage function parameters of a primitive type.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Primitive stage function parameter")
class PrimitiveDataTest
{
  @Test
  @DisplayName("Data value is unboxed")
  void unboxed()
  {
    try(var context = new AnnotationConfigApplicationContext(PrimitiveConfiguration.class)) {
      assertTrue(context.getBean(PrimitiveRunner.class).run(42));
      assertEquals(List.of(42, 42L), context.getBean(PrimitiveBean.class).log);
    }
  }


  @Test
  @DisplayName("Null data value fails")
  void nullValue()
  {
    try(var context = new AnnotationConfigApplicationContext(PrimitiveConfiguration.class)) {
      final var runner = context.getBean(PrimitiveRunner.class);
      final var exception = assertThrows(StageRunnerException.class, () -> runner.run(null));

      assertEquals("Data value 'count' must not be null", exception.getMessage());
      assertEquals(List.of(), context.getBean(PrimitiveBean.class).log);
    }
  }




  @FunctionalInterface
  interface PrimitiveRunner {
    boolean run(Integer count);
  }




  static class PrimitiveBean
  {
    final List<Object> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Unboxed")
    public void unboxed(int count) {
      log.add(count);
    }


    // Integer data value converted to long
    @StageDef(stage = TestStage.PROCESS, comment = "Converted")
    public void converted(long count) {
      log.add(count);
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
}
