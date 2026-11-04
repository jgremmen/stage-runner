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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Tests that a static stage function is registered once, regardless of the number of beans exposing it.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Static stage functions")
class StaticStageFunctionTest
{
  private static final List<String> LOG = new CopyOnWriteArrayList<>();


  @BeforeEach
  void clearLog() {
    LOG.clear();
  }


  @Test
  @DisplayName("Static function of a class with several beans is executed once")
  void severalBeansOfSameClass()
  {
    try(var context = new AnnotationConfigApplicationContext(SameClassConfiguration.class)) {
      context.getBean(StaticRunner.class).run("value");

      assertEquals(List.of("static:value", "instance:value", "instance:value"), LOG);
    }
  }


  @Test
  @DisplayName("Named static function inherited by several beans is registered once")
  void namedStaticFunctionInherited()
  {
    try(var context = new AnnotationConfigApplicationContext(InheritedConfiguration.class)) {
      context.getBean(StaticRunner.class).run("value");

      assertEquals(List.of("enabled:true", "named:value"), LOG);
    }
  }




  @FunctionalInterface
  interface StaticRunner {
    void run(String text);
  }




  @SuppressWarnings("unused")
  public static class StaticBean
  {
    @StageDef(stage = TestStage.INIT, comment = "Static")
    public static void staticFunction(String text) {
      LOG.add("static:" + text);
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Instance")
    public void instanceFunction(String text) {
      LOG.add("instance:" + text);
    }
  }




  @SuppressWarnings("unused")
  public abstract static class NamedBase
  {
    @StageDef(stage = TestStage.PROCESS, scope = "named", comment = "Named static")
    public static void namedFunction(String text) {
      LOG.add("named:" + text);
    }
  }




  @SuppressWarnings("unused")
  public static class EnablerBean
  {
    @StageDef(stage = TestStage.INIT, comment = "Enable named function")
    public void enable(StageContext<TestStage> context) {
      LOG.add("enabled:" + context.enableNamedStageFunction("named"));
    }
  }




  public static class FirstNamedBean extends NamedBase {
  }




  public static class SecondNamedBean extends NamedBase {
  }




  @Configuration(proxyBeanMethods = false)
  static class SameClassConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<StaticRunner> processor() {
      return new StageRunnerFactoryProcessor<>(StaticRunner.class, StageDef.class);
    }


    @Bean
    StaticBean firstBean() {
      return new StaticBean();
    }


    @Bean
    StaticBean secondBean() {
      return new StaticBean();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class InheritedConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<StaticRunner> processor() {
      return new StageRunnerFactoryProcessor<>(StaticRunner.class, StageDef.class);
    }


    @Bean
    FirstNamedBean firstBean() {
      return new FirstNamedBean();
    }


    @Bean
    SecondNamedBean secondBean() {
      return new SecondNamedBean();
    }


    @Bean
    EnablerBean enablerBean() {
      return new EnablerBean();
    }
  }
}
