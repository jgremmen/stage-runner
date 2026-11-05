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
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests that {@link Data @Data} annotations of stage function parameters are inherited from overridden and implemented
 * methods, consistent with the stage function annotation itself.
 * <p>
 * All stage function parameters are named {@code value}, which matches no data entry, and the runner passes 3 strings.
 * Each parameter can therefore only be bound through its inherited {@code @Data} annotation; otherwise the binding is
 * ambiguous and the context fails to start.
 *
 * @author Jeroen Gremmen
 * @since 0.4.1
 */
@DisplayName("Inherited @Data annotations")
class InheritedDataAnnotationTest
{
  @Test
  @DisplayName("@Data is inherited from interfaces, super-interfaces, generic interfaces and superclasses")
  void inheritedDataAnnotations()
  {
    try(var context = new AnnotationConfigApplicationContext(InheritanceConfiguration.class)) {
      assertTrue(context.getBean(InheritanceRunner.class).run("FIRST", "SECOND", "THIRD"));
      assertEquals(
          List.of("interface:SECOND", "superclass:THIRD", "generic:SECOND", "superInterface:THIRD", "overriding:FIRST"),
          context.getBean(InheritingBean.class).log);
    }
  }




  @FunctionalInterface
  interface InheritanceRunner {
    boolean run(String first, String second, String third);
  }




  interface SuperFunctions
  {
    @StageDef(stage = TestStage.CLEANUP, comment = "Super-interface")
    void fromSuperInterface(@Data("third") String value);
  }




  interface Functions extends SuperFunctions
  {
    @StageDef(stage = TestStage.INIT, comment = "Interface")
    void fromInterface(@Data("second") String value);


    @StageDef(stage = TestStage.END, comment = "Overridden @Data")
    void overriding(@Data("third") String value);
  }




  interface GenericFunctions<T>
  {
    @StageDef(stage = TestStage.PROCESS, comment = "Generic interface")
    void fromGeneric(@Data("second") T value);
  }




  @SuppressWarnings("unused")
  static abstract class BaseBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.START, comment = "Superclass")
    public void fromSuperclass(@Data("third") String value) {
      log.add("base:" + value);
    }
  }




  static class InheritingBean extends BaseBean implements Functions, GenericFunctions<String>
  {
    @Override
    public void fromInterface(String value) {
      log.add("interface:" + value);
    }


    @Override
    public void fromSuperclass(String value) {
      log.add("superclass:" + value);
    }


    @Override
    public void fromGeneric(String value) {
      log.add("generic:" + value);
    }


    @Override
    public void fromSuperInterface(String value) {
      log.add("superInterface:" + value);
    }


    // the annotation of the implementing method takes precedence over the inherited one
    @Override
    public void overriding(@Data("first") String value) {
      log.add("overriding:" + value);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class InheritanceConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<InheritanceRunner> processor() {
      return new StageRunnerFactoryProcessor<>(InheritanceRunner.class, StageDef.class);
    }


    @Bean
    InheritingBean inheritingBean() {
      return new InheritingBean();
    }
  }
}
