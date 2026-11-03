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

import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the detection of the functional method of a stage runner interface.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage runner functional interface")
class FunctionalInterfaceTest
{
  @Test
  @DisplayName("Default methods containing lambdas are ignored")
  void lambdaInDefaultMethod() throws Exception
  {
    assertFunctionalMethod(
        RunnerWithLambda.class,
        RunnerWithLambda.class.getMethod("run", String.class));
  }


  @Test
  @DisplayName("Static and private methods are ignored")
  void staticAndPrivateMethods() throws Exception
  {
    assertFunctionalMethod(
        RunnerWithStatic.class,
        RunnerWithStatic.class.getMethod("run", String.class));
  }


  @Test
  @DisplayName("Redeclared Object methods are ignored")
  void objectMethods() throws Exception
  {
    assertFunctionalMethod(
        RunnerWithObjectMethods.class,
        RunnerWithObjectMethods.class.getMethod("run", String.class));
  }


  @Test
  @DisplayName("Abstract method inherited from a super-interface is detected")
  void inheritedMethod() throws Exception
  {
    assertFunctionalMethod(
        InheritingRunner.class,
        BaseRunner.class.getMethod("run", String.class));
  }


  @Test
  @DisplayName("Abstract method inherited from multiple super-interfaces is counted once")
  void diamondMethod()
  {
    final var method = processor(DiamondRunner.class).getStageRunnerInterfaceMethod();

    assertEquals("run", method.getName());
    assertArrayEquals(new Class<?>[] { String.class }, method.getParameterTypes());
  }


  @Test
  @DisplayName("Interface with more than one abstract method is rejected")
  void twoAbstractMethods()
  {
    final var exception =
        assertThrows(StageRunnerConfigurationException.class, () -> processor(TwoMethodRunner.class));
    assertEquals(TwoMethodRunner.class.getName() + " is not a functional interface", exception.getMessage());
  }


  @Test
  @DisplayName("Interface with a static method only is rejected")
  void staticMethodOnly()
  {
    final var exception =
        assertThrows(StageRunnerConfigurationException.class, () -> processor(StaticOnlyRunner.class));
    assertEquals(StaticOnlyRunner.class.getName() + " has no functional method", exception.getMessage());
  }


  @Test
  @DisplayName("@Data without name on a stage runner interface parameter is rejected")
  void unnamedDataParameter()
  {
    var exception = assertThrows(StageRunnerConfigurationException.class, () -> processor(UnnamedDataRunner.class));

    assertTrue(exception.getMessage().startsWith(
        "@Data without name is not allowed for parameter 1 in stage runner function "), exception.getMessage());
  }


  @Test
  @DisplayName("@Data on the callback parameter of a stage runner interface is rejected")
  void dataOnCallbackParameter()
  {
    var exception = assertThrows(
        StageRunnerConfigurationException.class,
        () -> processor(DataCallbackRunner.class));

    assertTrue(exception.getMessage().startsWith(
        "@Data is not allowed for callback parameter 2 in stage runner function "), exception.getMessage());
  }


  @Test
  @DisplayName("Duplicate data name in a stage runner interface is rejected")
  void duplicateDataName()
  {
    var exception = assertThrows(
        StageRunnerConfigurationException.class,
        () -> processor(DuplicateDataRunner.class));

    assertTrue(exception.getMessage().startsWith(
        "duplicate data name 'foo' for parameter #2 in stage runner function "), exception.getMessage());
  }


  @Test
  @DisplayName("Stage runner with an inherited functional method is executed")
  void runInheritedMethod()
  {
    try(var context = new AnnotationConfigApplicationContext(InheritingConfiguration.class)) {
      assertTrue(context.getBean(InheritingRunner.class).run("bar"));
      assertEquals(List.of("bar"), context.getBean(InheritingBean.class).log);
    }
  }


  private static void assertFunctionalMethod(Class<?> interfaceType, Object expectedMethod) {
    assertEquals(expectedMethod, processor(interfaceType).getStageRunnerInterfaceMethod());
  }


  private static <R> StageRunnerFactoryProcessor<R> processor(Class<R> interfaceType) {
    return new StageRunnerFactoryProcessor<>(interfaceType, StageDef.class);
  }




  @SuppressWarnings("unused")
  interface RunnerWithLambda
  {
    boolean run(String foo);

    default Supplier<String> supplier() {
      return () -> "x";
    }
  }




  @SuppressWarnings("unused")
  interface RunnerWithStatic
  {
    boolean run(String foo);

    static void helper() {
      privateHelper();
    }

    private static void privateHelper() {
    }
  }




  interface RunnerWithObjectMethods
  {
    boolean run(String foo);

    boolean equals(Object o);

    int hashCode();

    String toString();
  }




  interface BaseRunner {
    boolean run(String foo);
  }




  interface InheritingRunner extends BaseRunner {
  }




  interface OtherBaseRunner {
    boolean run(String foo);
  }




  interface DiamondRunner extends BaseRunner, OtherBaseRunner {
  }




  @SuppressWarnings("unused")
  interface TwoMethodRunner
  {
    boolean run(String foo);

    void stop();
  }




  interface UnnamedDataRunner {
    boolean run(@Data String foo);
  }




  interface DataCallbackRunner {
    boolean run(String foo, @Data("callback") StageRunnerCallback<TestStage> callback);
  }




  interface DuplicateDataRunner {
    boolean run(String foo, @Data("foo") String bar);
  }




  interface StaticOnlyRunner
  {
    @SuppressWarnings("unused")
    static boolean run(String foo) {
      return true;
    }
  }




  @SuppressWarnings("unused")
  static class InheritingBean
  {
    final List<Object> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Inherited")
    public void inherited(String foo) {
      log.add(foo);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class InheritingConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<InheritingRunner> processor() {
      return new StageRunnerFactoryProcessor<>(InheritingRunner.class, StageDef.class);
    }


    @Bean
    InheritingBean inheritingBean() {
      return new InheritingBean();
    }
  }
}
