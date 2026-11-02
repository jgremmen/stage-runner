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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests stage functions declared on a package-private bean class with package-private parameter types.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@SpringJUnitConfig(PackagePrivateBeanTest.StageConfiguration.class)
@DisplayName("Package-private stage function bean")
class PackagePrivateBeanTest
{
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired PackagePrivateRunner runner;

  @Autowired PackagePrivateBean bean;


  @Test
  @DisplayName("Stage functions of a package-private bean are executed")
  void packagePrivateBean()
  {
    var item = new Item("first");

    assertTrue(runner.run(item));
    assertEquals(List.of("init:first", "process:first"), bean.log);
  }




  @FunctionalInterface
  interface PackagePrivateRunner {
    boolean run(Item item);
  }




  record Item(String name) {
  }




  static class PackagePrivateBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Initialize")
    public void init(Item item) {
      log.add("init:" + item.name());
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Process")
    public void process(Item item) {
      log.add("process:" + item.name());
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class StageConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<PackagePrivateRunner> processor() {
      return new StageRunnerFactoryProcessor<>(PackagePrivateRunner.class, StageDef.class);
    }


    @Bean
    PackagePrivateBean packagePrivateBean() {
      return new PackagePrivateBean();
    }
  }
}
