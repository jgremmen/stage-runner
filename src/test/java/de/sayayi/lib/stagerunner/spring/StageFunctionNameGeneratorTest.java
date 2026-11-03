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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Tests the handling of names provided by a custom stage function name generator.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage function name generator")
class StageFunctionNameGeneratorTest
{
  @Test
  @DisplayName("Empty generated name adds the stage function as an unnamed function")
  void emptyGeneratedName()
  {
    try(var context = new AnnotationConfigApplicationContext(EmptyNameConfiguration.class)) {
      context.getBean(NameRunner.class).run("value");

      assertEquals(List.of("first:value", "second:value"), context.getBean(NameBean.class).log);
    }
  }




  @FunctionalInterface
  interface NameRunner {
    void run(String text);
  }




  static class NameBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "First")
    public void first(String text) {
      log.add("first:" + text);
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Second")
    public void second(String text) {
      log.add("second:" + text);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class EmptyNameConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<NameRunner> processor()
    {
      final var processor = new StageRunnerFactoryProcessor<>(NameRunner.class, StageDef.class);

      // an empty name must behave like an empty @Name attribute instead of failing startup
      processor.setStageFunctionNameGenerator((attributes,method) -> "");

      return processor;
    }


    @Bean
    NameBean nameBean() {
      return new NameBean();
    }
  }
}
