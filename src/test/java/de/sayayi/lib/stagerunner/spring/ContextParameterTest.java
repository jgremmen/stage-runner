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
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests which stage function parameters receive the stage context and which receive data values.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage context parameter")
class ContextParameterTest
{
  @Test
  @DisplayName("Only parameters of type StageContext without @Data receive the stage context")
  void contextParameter()
  {
    try(var context = new AnnotationConfigApplicationContext(ContextConfiguration.class)) {
      assertTrue(context.getBean(ContextRunner.class).run("bar"));

      final var log = context.getBean(ContextBean.class).log;

      assertEquals(5, log.size());
      assertInstanceOf(StageContext.class, log.get(0));
      assertInstanceOf(StageContext.class, log.get(1));
      assertEquals("bar", log.get(2));
      assertEquals("bar", log.get(3));
      assertEquals("bar", log.get(4));
    }
  }




  @FunctionalInterface
  interface ContextRunner {
    boolean run(String foo);
  }




  @SuppressWarnings("unused")
  static class ContextBean
  {
    final List<Object> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Typed context")
    public void typedContext(StageContext<TestStage> context) {
      log.add(context);
    }


    @SuppressWarnings("rawtypes")
    @StageDef(stage = TestStage.START, comment = "Raw context")
    public void rawContext(StageContext context) {
      log.add(context);
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Annotated object")
    public void annotatedObject(@Data("foo") Object value) {
      log.add(value);
    }


    @StageDef(stage = TestStage.CLEANUP, comment = "Named object")
    public void namedObject(Object foo) {
      log.add(foo);
    }


    @StageDef(stage = TestStage.END, comment = "Unnamed @Data object")
    public void unnamedDataObject(@Data Object foo) {
      log.add(foo);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ContextConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<ContextRunner> processor() {
      return new StageRunnerFactoryProcessor<>(ContextRunner.class, StageDef.class);
    }


    @Bean
    ContextBean contextBean() {
      return new ContextBean();
    }
  }
}
