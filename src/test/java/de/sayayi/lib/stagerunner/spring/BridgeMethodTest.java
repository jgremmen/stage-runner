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

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.TestStage;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests stage functions implementing a generic interface method, for which the compiler generates an annotated bridge
 * method.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage function with bridge method")
class BridgeMethodTest
{
  @Test
  @DisplayName("Stage function implementing a generic interface method is registered once")
  void registeredOnce()
  {
    try(var context = new AnnotationConfigApplicationContext(PlainConfiguration.class)) {
      assertTrue(context.getBean(BridgeRunner.class).run("first"));
      assertEquals(List.of("handle:first"), context.getBean(StringHandler.class).log);
    }
  }


  @Test
  @DisplayName("Named stage function implementing a generic interface method is registered once")
  void namedRegisteredOnce()
  {
    try(var context = new AnnotationConfigApplicationContext(NamedConfiguration.class)) {
      assertTrue(context.getBean(BridgeRunner.class).run("first"));
      assertEquals(List.of("named:first"), context.getBean(NamedStringHandler.class).log);
    }
  }


  @Test
  @DisplayName("Stage function implementing a generic interface method is invoked through a JDK dynamic proxy")
  void invokeThroughJdkProxy() throws Exception
  {
    try(var context = new AnnotationConfigApplicationContext(ProxyConfiguration.class)) {
      var proxy = (Advised)context.getBean(Handler.class);
      var target = (StringHandler)proxy.getTargetSource().getTarget();
      var interceptor = (CountingInterceptor)proxy.getAdvisors()[0].getAdvice();

      assertNotNull(target);
      assertTrue(context.getBean(BridgeRunner.class).run("first"));
      assertEquals(List.of("handle:first"), target.log);
      assertEquals(1, interceptor.invocations.get());
    }
  }




  @FunctionalInterface
  interface BridgeRunner {
    boolean run(String value);
  }




  interface Handler<T> {
    void handle(T value);
  }




  static class StringHandler implements Handler<String>
  {
    final List<String> log = new ArrayList<>();


    @Override
    @StageDef(stage = TestStage.INIT, comment = "Handle")
    public void handle(String value) {
      log.add("handle:" + value);
    }
  }




  static class NamedStringHandler implements Handler<String>
  {
    final List<String> log = new ArrayList<>();


    @StageDef(stage = TestStage.INIT, comment = "Enable")
    public void enable(StageContext<TestStage> context) {
      assertTrue(context.enableNamedStageFunction("named"));
    }


    @Override
    @StageDef(scope = "named", stage = TestStage.PROCESS, comment = "Named")
    public void handle(String value) {
      log.add("named:" + value);
    }
  }




  static class CountingInterceptor implements MethodInterceptor
  {
    final AtomicInteger invocations = new AtomicInteger();


    @Override
    public Object invoke(org.aopalliance.intercept.MethodInvocation invocation) throws Throwable
    {
      invocations.incrementAndGet();
      return invocation.proceed();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class PlainConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<BridgeRunner> processor() {
      return new StageRunnerFactoryProcessor<>(BridgeRunner.class, StageDef.class);
    }


    @Bean
    StringHandler stringHandler() {
      return new StringHandler();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class NamedConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<BridgeRunner> processor() {
      return new StageRunnerFactoryProcessor<>(BridgeRunner.class, StageDef.class);
    }


    @Bean
    NamedStringHandler namedStringHandler() {
      return new NamedStringHandler();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ProxyConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<BridgeRunner> processor() {
      return new StageRunnerFactoryProcessor<>(BridgeRunner.class, StageDef.class);
    }


    // the target is not a bean itself, so its stage function is only discovered through the proxy
    @Bean
    Handler<String> handler()
    {
      var proxyFactory = new ProxyFactory(new StringHandler());

      proxyFactory.addInterface(Handler.class);
      proxyFactory.addAdvice(new CountingInterceptor());

      //noinspection unchecked
      return (Handler<String>)proxyFactory.getProxy();
    }
  }
}
