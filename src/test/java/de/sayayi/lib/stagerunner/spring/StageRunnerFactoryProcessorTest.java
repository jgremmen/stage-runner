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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the {@link StageRunnerFactoryProcessor} within a Spring application context.
 * <p>
 * Covered aspects are the detection of the functional method of the stage runner interface and running the generated
 * stage runner proxy, including the conversion and boxing of the bound values and a {@code null} data value.
 *
 * @since 0.3.0
 */
@SpringJUnitConfig(StageRunnerFactoryProcessorTest.StageConfiguration.class)
class StageRunnerFactoryProcessorTest
{
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired MyRunnerInterface myRunnerInterface;

  @Autowired MyBean myBean;


  @BeforeEach
  void clearLog() {
    myBean.log.clear();
  }


  @Test
  @DisplayName("Functional interface check")
  void functionalInterface() throws Exception
  {
    assertEquals(
        MyRunnerInterface.class.getMethod("run", String.class, List.class, int.class),
        StageRunnerFactoryProcessor.findFunctionalInterfaceMethod(MyRunnerInterface.class));
  }


  @Test
  @DisplayName("Stage functions receive the converted and boxed interface method arguments")
  void runInterface()
  {
    assertTrue(myRunnerInterface.run("important-task", List.of(1, 67, -4), 56));

    assertEquals(4, myBean.log.size());
    assertInstanceOf(StageContext.class, myBean.log.get(0));

    // List<Integer> converted to List<Long>
    assertEquals(List.of(1L, 67L, -4L), myBean.log.get(1));
    assertEquals("important-task", myBean.log.get(2));
    assertEquals(56, myBean.log.get(3));
  }


  @Test
  @DisplayName("Null data value must not throw NullPointerException")
  void runInterfaceWithNullDataValue()
  {
    assertTrue(myRunnerInterface.run("important-task", null, 56));

    assertEquals(4, myBean.log.size());
    assertNull(myBean.log.get(1));
  }




  @Configuration(proxyBeanMethods = false)
  @Import(MyBean.class)
  static class StageConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<MyRunnerInterface> processor() {
      return new StageRunnerFactoryProcessor<>(MyRunnerInterface.class, StageDef.class);
    }
  }




  @Component
  @SuppressWarnings("unused")
  public static class MyBean
  {
    final List<Object> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Initialize")
    public void init(StageContext<TestStage> context, List<Long> map)
    {
      log.add(context);
      log.add(map);
    }


    public void start() {
      log.add("start");
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Process task")
    public void task(String task, @Data(name = "count") int n)
    {
      log.add(task);
      log.add(n);
    }
  }
}
