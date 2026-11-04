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
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests stage functions declared on beans which are exposed as JDK dynamic proxies.
 * <p>
 * Covered aspects are invoking the stage function through the proxy interface, and rejecting a stage function at
 * startup which is not exposed by the proxy interface.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("JDK dynamic proxy stage function bean")
class JdkProxyBeanTest
{
  @Test
  @DisplayName("Stage function is invoked through the proxy interface")
  void invokeThroughProxy() throws Exception
  {
    try(var context = new AnnotationConfigApplicationContext(ProxyConfiguration.class)) {
      var proxy = (Advised)context.getBean(Service.class);
      var target = (ServiceImpl)proxy.getTargetSource().getTarget();
      var interceptor = (CountingInterceptor)proxy.getAdvisors()[0].getAdvice();

      assertTrue(AopUtils.isJdkDynamicProxy(proxy));
      assertNotNull(target);
      assertTrue(context.getBean(ProxyRunner.class).run("first"));
      assertEquals(List.of("handle:first"), target.log);
      assertEquals(1, interceptor.invocations.get());
    }
  }


  @Test
  @DisplayName("Stage function not exposed by the proxy interface is rejected at startup")
  void methodNotExposedByProxy()
  {
    var exception = assertThrows(Exception.class,
        () -> new AnnotationConfigApplicationContext(UnexposedConfiguration.class).close());

    for(Throwable cause = exception; cause != null; cause = cause.getCause())
      if (cause instanceof StageRunnerConfigurationException)
        return;

    fail("expected StageRunnerConfigurationException as cause of " + exception);
  }




  @FunctionalInterface
  interface ProxyRunner {
    boolean run(String value);
  }




  interface Service {
    void handle(String value);
  }




  static class ServiceImpl implements Service
  {
    final List<String> log = new ArrayList<>();


    @Override
    @StageDef(stage = TestStage.INIT, comment = "Handle")
    public void handle(String value) {
      log.add("handle:" + value);
    }
  }




  static class UnexposedServiceImpl implements Service
  {
    @Override
    public void handle(String value) {
    }


    @StageDef(stage = TestStage.PROCESS, comment = "Not exposed")
    public void notExposed() {
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




  static Service jdkProxy(Service target, CountingInterceptor interceptor)
  {
    var proxyFactory = new ProxyFactory(target);

    proxyFactory.addInterface(Service.class);
    proxyFactory.addAdvice(interceptor);

    return (Service)proxyFactory.getProxy();
  }




  @Configuration(proxyBeanMethods = false)
  static class ProxyConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<ProxyRunner> processor() {
      return new StageRunnerFactoryProcessor<>(ProxyRunner.class, StageDef.class);
    }


    // the target is not a bean itself, so its stage function is only discovered through the proxy
    @Bean
    Service service() {
      return jdkProxy(new ServiceImpl(), new CountingInterceptor());
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class UnexposedConfiguration
  {
    @Bean
    static StageRunnerFactoryProcessor<ProxyRunner> processor() {
      return new StageRunnerFactoryProcessor<>(ProxyRunner.class, StageDef.class);
    }


    @Bean
    Service service() {
      return jdkProxy(new UnexposedServiceImpl(), new CountingInterceptor());
    }
  }
}
