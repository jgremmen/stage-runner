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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests the use of a {@link ConversionService} bean by the {@link StageRunnerFactoryProcessor}.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Conversion service bean")
class ConversionServiceBeanTest
{
  @Test
  @DisplayName("Conversion service bean is post-processed and used for parameter conversion")
  void conversionServiceBean()
  {
    try(var context = new AnnotationConfigApplicationContext(ConversionConfiguration.class)) {
      assertTrue(context.getBean(RecordingPostProcessor.class).processedBeanNames.contains("conversionService"));

      assertTrue(context.getBean(AmountRunner.class).run("1250"));
      assertEquals(List.of(new Amount(1250)), context.getBean(AmountBean.class).log);
    }
  }


  @Test
  @DisplayName("Shared default conversion service is used if there is no unique conversion service bean")
  void ambiguousConversionServiceBeans()
  {
    try(var context = new AnnotationConfigApplicationContext(AmbiguousConfiguration.class)) {
      assertTrue(context.getBean(ListRunner.class).run(List.of(1, 2)));
      assertEquals(List.of(List.of(1L, 2L)), context.getBean(ListBean.class).log);
    }
  }




  @FunctionalInterface
  interface AmountRunner {
    boolean run(String amount);
  }




  @FunctionalInterface
  interface ListRunner {
    boolean run(List<Integer> values);
  }




  record Amount(long cents) {
  }




  static class AmountBean
  {
    final List<Amount> log = new ArrayList<>();


    @StageDef(stage = TestStage.PROCESS, comment = "Charge")
    @SuppressWarnings("unused")
    public void charge(Amount amount) {
      log.add(amount);
    }
  }




  static class ListBean
  {
    final List<List<Long>> log = new ArrayList<>();


    @StageDef(stage = TestStage.PROCESS, comment = "Values")
    public void values(List<Long> values) {
      log.add(values);
    }
  }




  static class RecordingPostProcessor implements BeanPostProcessor
  {
    final Set<String> processedBeanNames = ConcurrentHashMap.newKeySet();


    @Override
    public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName)
    {
      processedBeanNames.add(beanName);
      return bean;
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ConversionConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<AmountRunner> processor() {
      return new StageRunnerFactoryProcessor<>(AmountRunner.class, StageDef.class);
    }


    @Bean
    static RecordingPostProcessor recordingPostProcessor() {
      return new RecordingPostProcessor();
    }


    @Bean
    ConversionService conversionService()
    {
      var conversionService = new DefaultConversionService();
      conversionService.addConverter(String.class, Amount.class, s -> new Amount(Long.parseLong(s)));

      return conversionService;
    }


    @Bean
    AmountBean amountBean() {
      return new AmountBean();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class AmbiguousConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<ListRunner> processor() {
      return new StageRunnerFactoryProcessor<>(ListRunner.class, StageDef.class);
    }


    @Bean
    ConversionService conversionService1() {
      return new DefaultConversionService();
    }


    @Bean
    ConversionService conversionService2() {
      return new DefaultConversionService();
    }


    @Bean
    ListBean listBean() {
      return new ListBean();
    }
  }
}
