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
package de.sayayi.lib.stagerunner.it;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.StageRunnerFactoryProcessor;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import de.sayayi.lib.stagerunner.spring.annotation.StageDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests the stage runner library with both the library and the application running as named modules on the module path.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Module path")
class ModulePathTest
{
  @Test
  @DisplayName("Library and application are named modules")
  void namedModules()
  {
    assertEquals("de.sayayi.lib.stagerunner", StageContext.class.getModule().getName());
    assertEquals("de.sayayi.lib.stagerunner.it", ModulePathTest.class.getModule().getName());
  }


  @Test
  @DisplayName("Core stage runner")
  void coreStageRunner()
  {
    final var factory = new DefaultStageRunnerFactory<>(ItStage.class);
    final var log = new ArrayList<String>();

    factory.addStageFunction(ItStage.INIT, ctx -> log.add("init"));
    factory.addStageFunction(ItStage.PROCESS, ctx -> log.add("process:" + ctx.getData("task")));

    assertTrue(factory.createRunner().run(Map.of("task", "core")));
    assertEquals(List.of("init", "process:core"), log);
  }


  @Test
  @DisplayName("Spring integration")
  void springIntegration()
  {
    try(var context = new AnnotationConfigApplicationContext(ItConfiguration.class)) {
      assertTrue(context.getBean(ItRunner.class).run("spring", 42));

      assertEquals(List.of("init:spring", "process:spring:42"), context.getBean(PublicBean.class).log);
      assertEquals(List.of("package-private:42"), context.getBean(PackagePrivateBean.class).log);
    }
  }




  public enum ItStage {
    INIT, PROCESS
  }




  @Target(METHOD)
  @Retention(RUNTIME)
  public @interface ItStageDef
  {
    @StageDefinition.Stage
    ItStage stage();

    @StageDefinition.Description
    String comment() default "";
  }




  @FunctionalInterface
  public interface ItRunner {
    boolean run(String task, int count);
  }




  public static class PublicBean
  {
    final List<String> log = new ArrayList<>();


    @ItStageDef(stage = ItStage.INIT)
    public void init(StageContext<ItStage> context, String task) {
      log.add("init:" + task);
    }


    // int data value converted to long
    @ItStageDef(stage = ItStage.PROCESS)
    public void process(String task, @Data("count") long count) {
      log.add("process:" + task + ':' + count);
    }
  }




  static class PackagePrivateBean
  {
    final List<String> log = new ArrayList<>();


    @ItStageDef(stage = ItStage.PROCESS)
    public void process(int count) {
      log.add("package-private:" + count);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ItConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<ItRunner> processor() {
      return new StageRunnerFactoryProcessor<>(ItRunner.class, ItStageDef.class);
    }


    @Bean
    PublicBean publicBean() {
      return new PublicBean();
    }


    @Bean
    PackagePrivateBean packagePrivateBean() {
      return new PackagePrivateBean();
    }
  }
}
