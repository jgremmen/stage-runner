/*
 * Copyright 2024 Jeroen Gremmen
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringJUnitConfig(StageRunnerFactoryProcessorTest.StageConfiguration.class)
class StageRunnerFactoryProcessorTest
{
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired MyRunnerInterface myRunnerInterface;


  @Test
  @DisplayName("Functional interface check")
  void functionalInterface() throws Exception
  {
    assertEquals(
        MyRunnerInterface.class.getMethod("run", String.class, List.class, int.class),
        new StageRunnerFactoryProcessor<>(MyRunnerInterface.class, StageDef.class)
            .findFunctionalInterfaceMethod(MyRunnerInterface.class));
  }


  @Test
  void testInterface() {
    myRunnerInterface.run("important-task", Arrays.asList(1, 67, -4), 56);
  }




  @Configuration(proxyBeanMethods = false)
  @Import(MyBean.class)
  static class StageConfiguration
  {
    @Bean
    StageRunnerFactoryProcessor<MyRunnerInterface> processor() {
      return new StageRunnerFactoryProcessor<>(MyRunnerInterface.class, StageDef.class);
    }
  }




  @Component
  @SuppressWarnings("unused")
  public static class MyBean
  {
    @StageDef(stage = TestStage.INIT, comment = "Initialize")
    public void init(StageContext<TestStage> context, List<Long> map) {
    }


    public void start() {
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Process task")
    public void task(String task, @Data(name = "count") int n) {
    }
  }
}
