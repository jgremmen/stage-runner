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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests which bean methods are discovered as stage functions.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Annotated stage function method discovery")
class AnnotatedMethodDiscoveryTest
{
  @Test
  @DisplayName("Inherited and meta-annotated stage functions are discovered, non-public methods are not")
  void discovery()
  {
    try(var context = new AnnotationConfigApplicationContext(DiscoveryConfiguration.class)) {
      assertTrue(context.getBean(DiscoveryRunner.class).run());
      assertEquals(List.of("interface", "superclass", "composed"), context.getBean(DiscoveryBean.class).log);
    }
  }




  @FunctionalInterface
  interface DiscoveryRunner {
    boolean run();
  }




  @Target(METHOD)
  @Retention(RUNTIME)
  @StageDef(stage = TestStage.PROCESS, comment = "Composed")
  @interface ProcessStage {
  }




  interface StageFunctions
  {
    @StageDef(stage = TestStage.INIT, comment = "Interface")
    void fromInterface();
  }




  @SuppressWarnings("unused")
  static abstract class BaseBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.START, comment = "Superclass")
    public void fromSuperclass() {
      log.add("base");
    }
  }




  @SuppressWarnings("unused")
  static class DiscoveryBean extends BaseBean implements StageFunctions
  {
    @Override
    public void fromInterface() {
      log.add("interface");
    }


    @Override
    public void fromSuperclass() {
      log.add("superclass");
    }


    @ProcessStage
    public void composed() {
      log.add("composed");
    }


    @SuppressWarnings("unused")
    @StageDef(stage = TestStage.END, comment = "Package-private")
    void packagePrivate() {
      log.add("packagePrivate");
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class DiscoveryConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<DiscoveryRunner> processor() {
      return new StageRunnerFactoryProcessor<>(DiscoveryRunner.class, StageDef.class);
    }


    @Bean
    DiscoveryBean discoveryBean() {
      return new DiscoveryBean();
    }
  }
}
