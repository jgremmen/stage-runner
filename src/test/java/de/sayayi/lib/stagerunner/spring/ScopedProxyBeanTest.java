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
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.context.annotation.ScopedProxyMode.INTERFACES;
import static org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS;


/**
 * Tests stage functions declared on beans which are exposed through a scoped proxy, optionally combined with method
 * interceptors applied by Spring's auto proxy infrastructure.
 * <p>
 * A scoped proxy registers 2 beans: the scoped proxy itself and the hidden target bean ({@code scopedTarget.*}). Both
 * expose the same stage functions, which must be registered only once. Covered aspects are singleton and non-singleton
 * targets, class based and interface based scoped proxies, targets which are proxied themselves because of a Spring
 * interceptor (caching, async execution or a custom advisor) independent of the creation order of proxy and target, and
 * a singleton which is exposed by 2 bean names.
 * <p>
 * Each test also verifies that the stage function is registered with the bean exposed to the application and is invoked
 * through its proxies, so the interceptors of the bean are applied.
 *
 * @author Jeroen Gremmen
 * @since 0.4.1
 */
@DisplayName("Scoped proxy stage function bean")
class ScopedProxyBeanTest
{
  @Test
  @DisplayName("Stage function of a class based scoped proxy is registered once")
  void classBasedScopedProxy()
  {
    try(var context = createContext(ScopedService.class)) {
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("scoped:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service");
    }
  }


  @Test
  @DisplayName("Stage function of an interface based scoped proxy is registered once")
  void interfaceBasedScopedProxy()
  {
    try(var context = createContext(InterfaceScopedService.class)) {
      assertTrue(AopUtils.isJdkDynamicProxy(context.getBean("service")));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("interface:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service");
    }
  }


  @Test
  @DisplayName("Intercepted stage function of a class based scoped proxy is registered once")
  void interceptedClassBasedScopedProxy()
  {
    try(var context = createContext(InterceptedScopedService.class, AdvisorConfiguration.class)) {
      // the target has been created before the scoped proxy, which therefore reports the generated proxy class
      var targetClass = ((Advised)context.getBean("service")).getTargetClass();
      assertNotNull(targetClass);
      assertNotEquals(InterceptedScopedService.class, targetClass);
      assertEquals(InterceptedScopedService.class, ClassUtils.getUserClass(targetClass));
      assertTrue(AopUtils.isCglibProxy(context.getBean("scopedTarget.service")));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("intercepted:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service", "scopedTarget.service");
      assertEquals(1, context.getBean(CountingInterceptor.class).invocations.get());
    }
  }


  @Test
  @DisplayName("Intercepted stage function of a scoped proxy created before its target is registered once")
  void interceptedScopedProxyCreatedBeforeTarget()
  {
    try(var context = createContext(InterceptedScopedService.class, AdvisorConfiguration.class,
        EarlyConsumer.class)) {
      // the target has not been created before the scoped proxy, which therefore reports the user class
      assertEquals(InterceptedScopedService.class, ((Advised)context.getBean("service")).getTargetClass());
      assertTrue(AopUtils.isCglibProxy(context.getBean("scopedTarget.service")));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("intercepted:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service", "scopedTarget.service");
      assertEquals(1, context.getBean(CountingInterceptor.class).invocations.get());
    }
  }


  @Test
  @DisplayName("Intercepted stage function of a scoped proxy for an interface implementation is registered once")
  void interceptedInterfaceImplementationScopedProxy()
  {
    try(var context = createContext(InterceptedServiceImpl.class, AdvisorConfiguration.class)) {
      assertTrue(AopUtils.isCglibProxy(context.getBean("service")));
      assertTrue(AopUtils.isCglibProxy(context.getBean("scopedTarget.service")));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("impl:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service", "scopedTarget.service");
      assertEquals(1, context.getBean(CountingInterceptor.class).invocations.get());
    }
  }


  @Test
  @DisplayName("Cache evicting stage function of a class based scoped proxy is registered once")
  void cachingScopedProxy()
  {
    try(var context = createContext(CachingScopedService.class, CachingConfiguration.class)) {
      var cache = context.getBean(CacheManager.class).getCache("values");

      assertNotNull(cache);
      cache.put("k", "v");

      assertTrue(AopUtils.isCglibProxy(context.getBean("scopedTarget.service")));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("caching:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service", "scopedTarget.service");
      assertNull(cache.get("k"));
    }
  }


  @Test
  @DisplayName("Async stage function of a scoped proxy created before its target is registered once")
  void asyncScopedProxyCreatedBeforeTarget()
  {
    try(var context = createContext(AsyncScopedService.class, AsyncConfiguration.class,
        EarlyAsyncConsumer.class)) {
      // the async post processor does not predict the proxy type, so the scoped proxy reports the user class
      assertEquals(AsyncScopedService.class, ((Advised)context.getBean("service")).getTargetClass());
      assertTrue(AopUtils.isCglibProxy(context.getBean("scopedTarget.service")));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("async:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service", "scopedTarget.service");
      assertEquals(1, context.getBean(CountingExecutor.class).executions.get());
    }
  }


  @Test
  @DisplayName("Intercepted stage function of a singleton without scoped proxy is registered once")
  void interceptedSingleton()
  {
    try(var context = createContext(InterceptedSingleton.class, AdvisorConfiguration.class)) {
      assertTrue(AopUtils.isCglibProxy(context.getBean(InterceptedSingleton.class)));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("singleton:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service");
      assertEquals(1, context.getBean(CountingInterceptor.class).invocations.get());
    }
  }


  @Test
  @DisplayName("Stage function of a singleton exposed by 2 bean names is registered once")
  void singletonExposedTwice()
  {
    try(var context = createContext(InterceptedSingleton.class, ExposingConfiguration.class)) {
      assertSame(context.getBean("service"), context.getBean("exposedService"));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("singleton:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context);
    }
  }


  @Test
  @DisplayName("Stage function of a non-singleton scoped bean is invoked through its scoped proxy")
  void threadScopedProxy()
  {
    try(var context = createContext(ThreadScopedService.class, ThreadScopeConfiguration.class,
        AdvisorConfiguration.class)) {
      assertInstanceOf(ScopedObject.class, context.getBean("service"));
      assertTrue(context.getBean(ScopedRunner.class).run("a"));
      assertEquals(List.of("thread:a"), context.getBean(Log.class).entries);
      assertInvokedThrough(context, "service", "scopedTarget.service");
      assertEquals(1, context.getBean(CountingInterceptor.class).invocations.get());
    }
  }


  /**
   * Asserts that the only stage function is registered with the bean which is exposed to the application as
   * {@code service}, and that the stage function has been invoked through the classes of the given beans.
   */
  private static void assertInvokedThrough(AnnotationConfigApplicationContext context, String... beanNames)
  {
    var registeredBeans = context.getBean(RecordingProcessor.class).registeredBeans;

    assertEquals(1, registeredBeans.size());
    assertSame(context.getBean("service"), registeredBeans.getFirst());

    var callStack = context.getBean(Log.class).callStack;

    for(var beanName: beanNames)
    {
      var bean = context.getBean(beanName);

      assertTrue(AopUtils.isAopProxy(bean), beanName + " is not a proxy");
      assertTrue(callStack.contains(bean.getClass().getName()), "not invoked through " + beanName);
    }
  }


  private static AnnotationConfigApplicationContext createContext(Class<?> serviceClass,
                                                                  Class<?>... configurationClasses)
  {
    var context = new AnnotationConfigApplicationContext();

    context.register(ProcessorConfiguration.class);
    if (configurationClasses.length > 0)
      context.register(configurationClasses);
    context.registerBean("service", serviceClass);
    context.refresh();

    return context;
  }




  @FunctionalInterface
  interface ScopedRunner {
    boolean run(String value);
  }




  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Intercepted {
  }




  static class Log
  {
    final List<String> entries = new CopyOnWriteArrayList<>();
    volatile List<String> callStack = List.of();


    // records the classes on the call stack of the stage function, which include the proxies it was invoked through
    void add(String entry)
    {
      entries.add(entry);
      callStack = StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::getClassName).toList());
    }
  }




  static class CountingInterceptor implements MethodInterceptor
  {
    final AtomicInteger invocations = new AtomicInteger();


    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable
    {
      invocations.incrementAndGet();
      return invocation.proceed();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ProcessorConfiguration
  {
    @Bean
    static RecordingProcessor processor() {
      return new RecordingProcessor();
    }


    @Bean
    Log log() {
      return new Log();
    }
  }




  // records the beans the stage functions are registered with
  static class RecordingProcessor extends StageRunnerFactoryProcessor<ScopedRunner>
  {
    final List<Object> registeredBeans = new CopyOnWriteArrayList<>();


    RecordingProcessor()
    {
      super(ScopedRunner.class, StageDef.class);

      setStageFunctionFilter((bean, stage, order, name) -> registeredBeans.add(bean));
    }
  }




  // mimics the infrastructure of @Transactional: an auto proxy creator applying an annotation driven advisor
  @Configuration(proxyBeanMethods = false)
  static class AdvisorConfiguration
  {
    @Bean
    static DefaultAdvisorAutoProxyCreator autoProxyCreator()
    {
      var autoProxyCreator = new DefaultAdvisorAutoProxyCreator();
      autoProxyCreator.setProxyTargetClass(true);

      return autoProxyCreator;
    }


    @Bean
    static CountingInterceptor countingInterceptor() {
      return new CountingInterceptor();
    }


    @Bean
    static Advisor interceptedAdvisor(CountingInterceptor countingInterceptor)
    {
      return new DefaultPointcutAdvisor(
          AnnotationMatchingPointcut.forMethodAnnotation(Intercepted.class), countingInterceptor);
    }
  }




  @EnableCaching(proxyTargetClass = true)
  @Configuration(proxyBeanMethods = false)
  static class CachingConfiguration
  {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("values");
    }
  }




  // registered before the service, so the scoped proxy is created before its target
  @SuppressWarnings("unused")
  static class EarlyConsumer
  {
    EarlyConsumer(InterceptedScopedService service) {
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ExposingConfiguration
  {
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    Object exposedService(@Qualifier("service") InterceptedSingleton service) {
      return service;
    }
  }




  @EnableAsync(proxyTargetClass = true)
  @Configuration(proxyBeanMethods = false)
  static class AsyncConfiguration
  {
    @Bean
    CountingExecutor taskExecutor() {
      return new CountingExecutor();
    }
  }




  // registered before the service, so the scoped proxy is created before its target
  static class EarlyAsyncConsumer
  {
    EarlyAsyncConsumer(AsyncScopedService service) {
    }
  }




  // runs async methods synchronously, so their effect can be verified immediately
  static class CountingExecutor implements TaskExecutor
  {
    final AtomicInteger executions = new AtomicInteger();


    @Override
    public void execute(@NotNull Runnable task)
    {
      executions.incrementAndGet();
      task.run();
    }
  }




  @Configuration(proxyBeanMethods = false)
  static class ThreadScopeConfiguration
  {
    @Bean
    static CustomScopeConfigurer threadScope()
    {
      var configurer = new CustomScopeConfigurer();
      configurer.addScope("thread", new SimpleThreadScope());

      return configurer;
    }
  }




  @Scope(proxyMode = TARGET_CLASS)
  static class ScopedService
  {
    private final Log log;


    ScopedService(Log log) {
      this.log = log;
    }


    @StageDef(stage = TestStage.INIT, comment = "Scoped")
    public void handle(String value) {
      log.add("scoped:" + value);
    }
  }




  interface Service {
    void handle(String value);
  }




  @Scope(proxyMode = INTERFACES)
  static class InterfaceScopedService implements Service
  {
    private final Log log;


    InterfaceScopedService(Log log) {
      this.log = log;
    }


    @Override
    @StageDef(stage = TestStage.INIT, comment = "Interface scoped")
    public void handle(String value) {
      log.add("interface:" + value);
    }
  }




  @Scope(proxyMode = TARGET_CLASS)
  @SuppressWarnings("unused")
  static class InterceptedScopedService
  {
    private final Log log;


    InterceptedScopedService(Log log) {
      this.log = log;
    }


    @Intercepted
    @StageDef(stage = TestStage.INIT, comment = "Intercepted")
    public void handle(String value) {
      log.add("intercepted:" + value);
    }
  }




  @Scope(proxyMode = TARGET_CLASS)
  static class InterceptedServiceImpl implements Service
  {
    private final Log log;


    InterceptedServiceImpl(Log log) {
      this.log = log;
    }


    @Override
    @Intercepted
    @StageDef(stage = TestStage.INIT, comment = "Intercepted implementation")
    public void handle(String value) {
      log.add("impl:" + value);
    }
  }




  @Scope(proxyMode = TARGET_CLASS)
  @SuppressWarnings("unused")
  static class CachingScopedService
  {
    private final Log log;


    CachingScopedService(Log log) {
      this.log = log;
    }


    @CacheEvict(cacheNames = "values", allEntries = true)
    @StageDef(stage = TestStage.INIT, comment = "Caching")
    public void handle(String value) {
      log.add("caching:" + value);
    }
  }




  @Scope(proxyMode = TARGET_CLASS)
  @SuppressWarnings("unused")
  static class AsyncScopedService
  {
    private final Log log;


    AsyncScopedService(Log log) {
      this.log = log;
    }


    @Async
    @StageDef(stage = TestStage.INIT, comment = "Async")
    public void handle(String value) {
      log.add("async:" + value);
    }
  }




  @SuppressWarnings("unused")
  static class InterceptedSingleton
  {
    private final Log log;


    InterceptedSingleton(Log log) {
      this.log = log;
    }


    @Intercepted
    @StageDef(stage = TestStage.INIT, comment = "Intercepted singleton")
    public void handle(String value) {
      log.add("singleton:" + value);
    }
  }




  @Scope(scopeName = "thread", proxyMode = TARGET_CLASS)
  @SuppressWarnings("unused")
  static class ThreadScopedService
  {
    private final Log log;


    ThreadScopedService(Log log) {
      this.log = log;
    }


    @Intercepted
    @StageDef(stage = TestStage.INIT, comment = "Thread scoped")
    public void handle(String value) {
      log.add("thread:" + value);
    }
  }
}
