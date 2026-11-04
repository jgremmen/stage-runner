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
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the discovery of stage functions on singleton beans independent of their creation time.
 * <p>
 * Covered aspects are the discovery on all kinds of singleton beans, not creating lazy-init singletons without stage
 * functions, and rejecting a stage runner execution before the discovery has completed.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage function discovery")
class StageFunctionDiscoveryTest
{
  @Test
  @DisplayName("Stage functions of all kinds of singleton beans are discovered")
  void allSingletonsAreDiscovered()
  {
    try(var context = new AnnotationConfigApplicationContext(DiscoveryConfiguration.class)) {
      var log = context.getBean(Log.class);

      assertTrue(context.getBean(DiscoveryRunner.class).run());
      assertEquals(List.of("lazy", "factoryBean", "postProcessor"), log.entries);
    }
  }


  @Test
  @DisplayName("Lazy-init singleton without stage functions is not created")
  void lazyBeanWithoutStageFunctionsIsNotCreated()
  {
    try(var context = new AnnotationConfigApplicationContext(DiscoveryConfiguration.class)) {
      assertTrue(context.getBeanFactory().containsSingleton("lazyBean"));
      assertFalse(context.getBeanFactory().containsSingleton("lazyBeanWithoutStageFunctions"));
    }
  }


  @Test
  @DisplayName("Running the stage runner during startup fails")
  void runDuringStartupFails()
  {
    final var exception = assertThrows(Exception.class,
        () -> new AnnotationConfigApplicationContext(EarlyRunConfiguration.class).close());

    for(Throwable cause = exception; cause != null; cause = cause.getCause())
      if (cause.getClass() == StageRunnerException.class)
        return;

    fail("expected StageRunnerException as cause of " + exception);
  }




  @FunctionalInterface
  interface DiscoveryRunner {
    boolean run();
  }




  static class Log {
    final List<String> entries = new CopyOnWriteArrayList<>();
  }




  static class LazyBean
  {
    private final Log log;


    LazyBean(Log log) {
      this.log = log;
    }


    @StageDef(stage = TestStage.INIT, comment = "Lazy")
    public void lazy() {
      log.entries.add("lazy");
    }
  }




  static class LazyBeanWithoutStageFunctions {
  }




  static class Product
  {
    private final Log log;


    Product(Log log) {
      this.log = log;
    }


    @StageDef(stage = TestStage.START, comment = "Factory bean product")
    public void product() {
      log.entries.add("factoryBean");
    }
  }




  static class ProductFactoryBean implements FactoryBean<Product>
  {
    private final Log log;


    ProductFactoryBean(Log log) {
      this.log = log;
    }


    @Override
    public Product getObject() {
      return new Product(log);
    }


    @Override
    public Class<?> getObjectType() {
      return Product.class;
    }
  }




  // bean post processors are not processed by other bean post processors
  static class StageFunctionPostProcessor implements BeanPostProcessor
  {
    final Log log = new Log();


    @StageDef(stage = TestStage.PROCESS, comment = "Post processor")
    public void postProcessor() {
      log.entries.add("postProcessor");
    }
  }




  static class EarlyCaller implements InitializingBean
  {
    private final DiscoveryRunner runner;


    EarlyCaller(DiscoveryRunner runner) {
      this.runner = runner;
    }


    @Override
    public void afterPropertiesSet() {
      runner.run();
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
    static StageFunctionPostProcessor stageFunctionPostProcessor() {
      return new StageFunctionPostProcessor();
    }


    // shares the log of the post processor, which cannot depend on regular beans
    @Bean
    Log log(StageFunctionPostProcessor stageFunctionPostProcessor) {
      return stageFunctionPostProcessor.log;
    }


    @Bean
    @Lazy
    LazyBean lazyBean(Log log) {
      return new LazyBean(log);
    }


    @Bean
    @Lazy
    LazyBeanWithoutStageFunctions lazyBeanWithoutStageFunctions() {
      return new LazyBeanWithoutStageFunctions();
    }


    @Bean
    ProductFactoryBean product(Log log) {
      return new ProductFactoryBean(log);
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class EarlyRunConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<DiscoveryRunner> processor() {
      return new StageRunnerFactoryProcessor<>(DiscoveryRunner.class, StageDef.class);
    }


    @Bean
    EarlyCaller earlyCaller(DiscoveryRunner runner) {
      return new EarlyCaller(runner);
    }
  }
}
