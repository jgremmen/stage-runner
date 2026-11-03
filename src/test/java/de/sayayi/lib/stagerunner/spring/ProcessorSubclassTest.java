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

import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests that a {@link StageRunnerFactoryProcessor} subclass can customize the stage function registration using the
 * protected accessors.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage runner factory processor subclass")
class ProcessorSubclassTest
{
  @Test
  @DisplayName("Subclass registers stage functions through the protected accessors")
  void customRegistration()
  {
    try(var context = new AnnotationConfigApplicationContext(SubclassConfiguration.class)) {
      final var processor = context.getBean(CustomProcessor.class);

      assertTrue(context.getBean(SubclassRunner.class).run("value"));

      assertEquals(SubclassRunner.class, processor.getStageRunnerInterfaceType());
      assertEquals("run", processor.getStageRunnerInterfaceMethod().getName());
      assertArrayEquals(new String[] { "text" }, processor.getDataNames());
      assertEquals(Set.of("text"), processor.getDataNameTypeMap().keySet());
      assertEquals(String.class, processor.getDataNameTypeMap().get("text").resolve());
      assertNotNull(processor.getBeanFactory());
      assertNotNull(processor.getStageRunnerProxyBuilder());

      // the subclass registered the function with a fixed order instead of the annotated one
      assertEquals(List.of("INIT#1"), processor.registered);
      assertEquals(List.of("value"), context.getBean(SubclassBean.class).log);
    }
  }




  @FunctionalInterface
  interface SubclassRunner {
    boolean run(String text);
  }




  static class CustomProcessor extends StageRunnerFactoryProcessor<SubclassRunner>
  {
    final List<String> registered = new ArrayList<>();


    CustomProcessor() {
      super(SubclassRunner.class, StageDef.class);
    }


    @Override
    protected void registerStageFunction(@NotNull AnnotationAttributes stageFunctionAnnotationAttributes,
                                         @NotNull Method method, @NotNull Object bean)
    {
      final TestStage stage = (TestStage)getStageFunctionAnnotation().getStage(stageFunctionAnnotationAttributes);

      if (getStageFunctionFilter().filter(bean, stage, 1, null))
      {
        final DefaultStageRunnerFactory<TestStage> factory = getStageRunnerFactory();
        final StageFunction<TestStage> function = getStageFunctionBuilder()
            .createStageFunction(getStageFunctionAnnotation(), getDataNameTypeMap(), method, bean);

        factory.addStageFunction(stage, 1, function);
        registered.add(stage + "#1");
      }
    }
  }




  static class SubclassBean
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, priority = 5, comment = "Log")
    public void log(String text) {
      log.add(text);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class SubclassConfiguration
  {
    @Bean
    static CustomProcessor processor() {
      return new CustomProcessor();
    }


    @Bean
    SubclassBean subclassBean() {
      return new SubclassBean();
    }
  }
}
