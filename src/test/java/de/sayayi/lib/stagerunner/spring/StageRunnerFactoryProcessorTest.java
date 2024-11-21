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
