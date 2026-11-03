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

import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import de.sayayi.lib.stagerunner.spring.builder.StageFunctionBuilderImpl;
import de.sayayi.lib.stagerunner.spring.builder.StageRunnerProxyBuilderImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotationAttributes;


/**
 * Spring post processor that binds a user defined stage runner interface to the beans in the application context.
 * <p>
 * On startup this processor registers a singleton bean implementing the stage runner interface. Once all non lazy
 * singletons have been instantiated, it inspects every singleton bean for methods carrying the configured stage
 * function annotation. Matching methods are turned into stage functions and registered with an internal
 * {@link StageRunnerFactory} which the proxy delegates to. Lazy-init singletons declaring stage functions are created
 * at that point; lazy-init singletons without stage functions are not. The stage runner cannot be run before the stage
 * functions have been discovered (see {@link #afterSingletonsInstantiated()}).
 * <p>
 * The behavior can be customized by supplying a {@link StageFunctionFilter}, a {@link StageFunctionBuilder}, a
 * {@link StageRunnerProxyBuilder}, a {@link ConversionService} or a stage function name generator through the various
 * setter methods.
 * <p>
 * The functional method of the stage runner interface must return {@code boolean} or {@code void}. Its parameters are
 * passed to the stage runner as data, named by the {@link Data @Data} annotation or the parameter name. A parameter of
 * type {@link StageRunnerCallback} is not passed as data but used as callback for the execution.
 * <p>
 * The processor is registered as a bean, for example through a {@code static} bean method in a Spring configuration
 * class:
 * <pre>
 * &#x40;Bean
 * static StageRunnerFactoryProcessor&lt;MyRunner&gt; myRunnerProcessor() {
 *   return new StageRunnerFactoryProcessor&lt;&gt;(MyRunner.class, MyStageDef.class);
 * }
 * </pre>
 *
 * @param <R>  stage runner interface type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@SuppressWarnings("rawtypes")
public class StageRunnerFactoryProcessor<R>
    implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware, InitializingBean, SmartInitializingSingleton
{
  /** Logger used by this processor. */
  protected final Log logger = LogFactory.getLog(StageRunnerFactoryProcessor.class);

  /** Stage runner interface implemented by the registered proxy bean. */
  protected final Class<R> stageRunnerInterfaceType;

  /** Descriptor of the stage function annotation marking the bean methods to register. */
  protected final StageFunctionAnnotation stageFunctionAnnotation;

  /** Stage runner factory populated with the discovered stage functions and used by the proxy. */
  protected final DefaultStageRunnerFactory stageRunnerFactory;

  /** Functional method of the stage runner interface. */
  protected final Method stageRunnerInterfaceMethod;

  /**
   * Data names for the parameters of {@link #stageRunnerInterfaceMethod}, indexed by parameter position. An entry is
   * {@code null} for a callback parameter.
   */
  protected final String[] dataNames;

  /** Unmodifiable mapping of data names to the types of the corresponding interface method parameters. */
  protected final Map<String,ResolvableType> dataNameTypeMap;

  /** Bean factory owning this processor. */
  protected BeanFactory beanFactory;

  /** Conversion service used by the default stage function builder, or {@code null} if not yet determined. */
  protected ConversionService conversionService;

  /** Builder for the stage runner proxy, or {@code null} to use the default implementation. */
  protected StageRunnerProxyBuilder stageRunnerProxyBuilder;

  /**
   * Builder for stage functions, or {@code null} to use the default implementation. Access it through
   * {@link #getStageFunctionBuilder()}, which creates the default implementation on first use.
   */
  protected StageFunctionBuilder stageFunctionBuilder;

  /** Whether annotations of the interface method are copied to the generated proxy method. */
  protected boolean copyInterfaceMethodAnnotations;

  /** Generator providing the stage function name for an annotated method, or {@code null} for unnamed functions. */
  protected BiFunction<AnnotationAttributes,Method,String> stageFunctionNameGenerator;

  /** Whether the stage functions of all singleton beans have been discovered and registered. */
  private volatile boolean stageFunctionsDiscovered;

  /** Filter deciding which discovered stage functions are registered; accepts all functions by default. */
  protected StageFunctionFilter stageFunctionFilter = (bean, stage, order, name) -> true;


  /**
   * Creates a stage runner factory processor for the given stage runner interface and stage function annotation type.
   *
   * @param stageRunnerInterfaceType      stage runner interface type, not {@code null}
   * @param stageFunctionAnnotationType   stage function annotation type, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage function annotation is malformed or the stage runner
   *                                            interface is not a valid functional interface
   * @throws StageRunnerException               if a data name cannot be determined or is used for more than one
   *                                            parameter of the interface method
   */
  @SuppressWarnings("unchecked")
  public StageRunnerFactoryProcessor(@NotNull Class<R> stageRunnerInterfaceType,
                                     @NotNull Class<? extends Annotation> stageFunctionAnnotationType)
  {
    var stageType =
        (stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotationType)).getStageType();
    logger.debug("stage type = " + stageType);

    this.stageRunnerFactory = new DefaultStageRunnerFactory(stageType) {
      @Override
      public @NotNull StageRunner createRunner()
      {
        // running before discovery has completed would silently execute an incomplete set of stage functions
        if (!stageFunctionsDiscovered)
        {
          throw new StageRunnerException("stage runner " + stageRunnerInterfaceType.getName() +
              " cannot be run before the stage functions of all singleton beans have been discovered");
        }

        return super.createRunner();
      }
    };
    this.stageRunnerInterfaceType = stageRunnerInterfaceType;
    this.stageRunnerInterfaceMethod = findFunctionalInterfaceMethod(stageRunnerInterfaceType);
    logger.debug("stage runner interface method = " + stageRunnerInterfaceMethod);

    var parameters = stageRunnerInterfaceMethod.getParameters();
    var parameterCount = parameters.length;

    dataNames = new String[parameterCount];

    var tmpDataNameTypeMap = new HashMap<String,ResolvableType>();
    var callbackType = forClassWithGenerics(StageRunnerCallback.class, stageType);
    var parameterNames = new DefaultParameterNameDiscoverer().getParameterNames(stageRunnerInterfaceMethod);
    ResolvableType resolvableType;

    for(int p = 0; p < parameterCount; p++)
      if (!callbackType.isAssignableFrom(resolvableType = forMethodParameter(stageRunnerInterfaceMethod, p)))
      {
        var dataName =
            getDataNameForParameter(findMergedAnnotation(parameters[p], Data.class), parameterNames, p);

        if (tmpDataNameTypeMap.put(dataNames[p] = dataName, resolvableType) != null)
        {
          throw new StageRunnerException("duplicate data name '" + dataName + "' for parameter #" + (p + 1) +
              " in stage runner function " + stageRunnerInterfaceMethod);
        }
      }

    dataNameTypeMap = Map.copyOf(tmpDataNameTypeMap);
    logger.debug("stage runner data = " + dataNameTypeMap);

    stageFunctionNameGenerator = (stageFunctionAnnotationAttributes,method) ->
        stageFunctionAnnotation.getName(stageFunctionAnnotationAttributes);
  }


  /**
   * Resolves the data name for a parameter of the stage runner interface method. The name is taken from the
   * {@link Data @Data} annotation when present, otherwise the reflected parameter name is used.
   *
   * @param dataAnnotation  optional {@link Data @Data} annotation on the parameter, may be {@code null}
   * @param parameterNames  reflected parameter names of the stage runner interface method, may be {@code null}
   * @param p               index of the parameter to resolve
   *
   * @return  the resolved data name, never {@code null}
   *
   * @throws StageRunnerException  if no name can be derived for the parameter
   */
  @Contract(pure = true)
  protected @NotNull String getDataNameForParameter(@Nullable Data dataAnnotation,
                                                    @Nullable String[] parameterNames,
                                                    int p)
  {
    var parameterName = dataAnnotation != null ? dataAnnotation.name() : "";
    if (parameterName.isEmpty() && parameterNames != null)
      parameterName = parameterNames[p];

    if (parameterName == null || parameterName.isEmpty())
    {
      throw new StageRunnerException("unable to detect data name for parameter " + (p + 1) +
          " in stage runner function " + stageRunnerInterfaceMethod + "; please specify @Data");
    }

    return parameterName;
  }


  /**
   * {@inheritDoc}
   * <p>
   * A missing {@link StageRunnerProxyBuilder} is supplied with a default implementation. The default
   * {@link StageFunctionBuilder} is not created here, but when the first stage function is registered (see
   * {@link #getStageFunctionBuilder()}).
   */
  @Override
  public void afterPropertiesSet()
  {
    if (stageRunnerProxyBuilder == null)
    {
      logger.trace("set default stage runner proxy builder");
      setStageRunnerProxyBuilder(new StageRunnerProxyBuilderImpl(copyInterfaceMethodAnnotations));
    }
  }


  /**
   * Returns the stage function builder, creating the default implementation on first use if none has been set.
   * <p>
   * The default builder uses the configured {@link ConversionService}. If none has been set, the unique
   * {@code ConversionService} bean of the bean factory is used, or the shared default conversion service if there is no
   * unique bean.
   * <p>
   * This processor is a {@link BeanDefinitionRegistryPostProcessor} and is therefore initialized before any bean post
   * processor has been registered. Looking up the conversion service lazily, when the first stage function is
   * registered, makes sure the conversion service bean is created and post-processed like any other bean.
   *
   * @return  stage function builder, never {@code null}
   *
   * @since 0.4.0
   */
  protected synchronized @NotNull StageFunctionBuilder getStageFunctionBuilder()
  {
    if (stageFunctionBuilder == null)
    {
      if (conversionService == null)
      {
        conversionService = beanFactory
            .getBeanProvider(ConversionService.class)
            .getIfUnique(DefaultConversionService::getSharedInstance);
      }

      logger.trace("set default stage function builder");
      setStageFunctionBuilder(new StageFunctionBuilderImpl(conversionService));
    }

    return stageFunctionBuilder;
  }


  /**
   * {@inheritDoc}
   * <p>
   * This processor does not modify the bean factory itself; bean definition registration is done in
   * {@link #postProcessBeanDefinitionRegistry(BeanDefinitionRegistry)}.
   */
  @Override
  public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) {
    // not interested in doing anything here
  }


  /**
   * {@inheritDoc}
   * <p>
   * Discovers the stage functions of all singleton beans and registers them with the internal stage runner factory.
   * This happens once, after all non lazy singletons have been instantiated, so the result does not depend on the order
   * in which beans are created:
   * <ul>
   *   <li>Singletons which already exist are analysed using their instance.</li>
   *   <li>Singletons which have not been created yet, like lazy-init singletons or objects created by factory beans,
   *       are analyzed by type. They are only created if they declare stage functions.</li>
   * </ul>
   * Non singleton beans are skipped. Until this method has completed, running the stage runner fails with a
   * {@link StageRunnerException}.
   *
   * @throws StageRunnerConfigurationException  if a stage function of a bean cannot be registered
   *
   * @since 0.4.0
   */
  @Override
  public void afterSingletonsInstantiated()
  {
    if (!(beanFactory instanceof ConfigurableListableBeanFactory listableBeanFactory))
      throw new IllegalStateException("bean factory must be a ConfigurableListableBeanFactory");

    for(var beanName: listableBeanFactory.getBeanNamesForType(Object.class, false, false))
    {
      try {
        if (listableBeanFactory.containsSingleton(beanName) && !listableBeanFactory.isFactoryBean(beanName))
          analyseStageFunctions(listableBeanFactory.getBean(beanName));
        else
        {
          var beanType = listableBeanFactory.getType(beanName, false);
          if (beanType != null && hasStageFunctions(ClassUtils.getUserClass(beanType)))
            analyseStageFunctions(listableBeanFactory.getBean(beanName));
        }
      } catch(StageRunnerConfigurationException ex) {
        throw new StageRunnerConfigurationException(
            "failed to register stage functions of bean '" + beanName + "': " + ex.getMessage(), ex);
      }
    }

    stageFunctionsDiscovered = true;
  }


  /**
   * Scans the given bean for methods carrying the configured stage function annotation and registers each match.
   *
   * @param bean  the bean to inspect, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if an annotated method cannot be registered as a stage function
   *
   * @see #isStageFunction(Method)
   */
  @SuppressWarnings("DataFlowIssue")
  protected void analyseStageFunctions(@NotNull Object bean)
  {
    final var annotationType = stageFunctionAnnotation.getAnnotationType();

    for(var method: ultimateTargetClass(bean).getMethods())
      if (isStageFunction(method))
      {
        registerStageFunction(
            findMergedAnnotationAttributes(method, annotationType, false, false),
            method, bean);
      }
  }


  /**
   * Tells whether the given type declares at least one stage function method.
   *
   * @param type  bean type, not {@code null}
   *
   * @return  {@code true} if {@code type} declares a stage function method, {@code false} otherwise
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected boolean hasStageFunctions(@NotNull Class<?> type)
  {
    for(var method: type.getMethods())
      if (isStageFunction(method))
        return true;

    return false;
  }


  /**
   * Tells whether the given method is a stage function method, i.e. it carries the configured stage function
   * annotation.
   * <p>
   * Bridge and synthetic methods are not considered. The compiler copies the annotations of a method to its bridge
   * methods, so registering them would add the same stage function more than once.
   *
   * @param method  method to check, not {@code null}
   *
   * @return  {@code true} if {@code method} is a stage function method, {@code false} otherwise
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected boolean isStageFunction(@NotNull Method method)
  {
    return !method.isBridge() && !method.isSynthetic() &&
        method.isAnnotationPresent(stageFunctionAnnotation.getAnnotationType());
  }


  /**
   * Registers a single annotated method as a stage function on the internal stage runner factory. Registration is
   * skipped when the configured {@link StageFunctionFilter} rejects the function. If a name is provided by the name
   * generator the function is registered as a named function, otherwise it is added anonymously.
   *
   * @param stageFunctionAnnotationAttributes  merged attributes of the stage function annotation, not {@code null}
   * @param method                             the annotated method, not {@code null}
   * @param bean                               the bean the method belongs to, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the method cannot be adapted to a stage function or the function
   *                                            name is not unique
   */
  @SuppressWarnings("unchecked")
  protected void registerStageFunction(@NotNull AnnotationAttributes stageFunctionAnnotationAttributes,
                                       @NotNull Method method,
                                       @NotNull Object bean)
  {
    var stageEnum = stageFunctionAnnotation.getStage(stageFunctionAnnotationAttributes);
    var order = stageFunctionAnnotation.getOrder(stageFunctionAnnotationAttributes);
    var name = stageFunctionNameGenerator.apply(stageFunctionAnnotationAttributes, method);

    if (stageFunctionFilter.filter(bean, stageEnum, order, name))
    {
      var description = stageFunctionAnnotation.getDescription(stageFunctionAnnotationAttributes);
      var function = getStageFunctionBuilder()
          .createStageFunction(stageFunctionAnnotation, dataNameTypeMap, method, bean);

      if (logger.isDebugEnabled())
      {
        var msg = new StringBuilder();

        if (name == null)
          msg.append("add stage function");
        else
          msg.append("register named stage function '").append(name).append("'");

        msg.append(", stage ").append(stageEnum).append('#').append(order);

        if (description != null)
          msg.append(", description '").append(description).append('\'');

        msg.append(": ").append(function);

        logger.debug(msg.toString());
      }

      if (name != null)
        stageRunnerFactory.namedStageFunction(name, stageEnum, order, description, function);
      else
        stageRunnerFactory.addStageFunction(stageEnum, order, description, function);
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Registers a lazily initialized primary singleton bean definition for the stage runner interface, named after the
   * fully qualified interface name. The bean is created through {@link #createStageRunnerProxy()} on first access.
   *
   * @param beanDefinitionRegistry  the target bean definition registry, not {@code null}
   */
  @Override
  public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry beanDefinitionRegistry)
  {
    var bean = new RootBeanDefinition(stageRunnerInterfaceType, SCOPE_SINGLETON, this::createStageRunnerProxy);

    bean.setTargetType(ResolvableType.forClass(stageRunnerInterfaceType));
    bean.setLazyInit(true);
    bean.setDescription("Auto-detected StageRunner for " + stageFunctionAnnotation.getStageType().getName());
    bean.setPrimary(true);

    var name = stageRunnerInterfaceType.getName();

    logger.trace("register singleton bean: " + name);

    beanDefinitionRegistry.registerBeanDefinition(name, bean);
  }


  /**
   * Factory method used by the singleton bean definition to lazily create the stage runner proxy.
   *
   * @param <S>  stage enumeration type
   *
   * @return  the stage runner proxy instance, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if the proxy cannot be created
   */
  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  protected <S extends Enum<S>> @NotNull R createStageRunnerProxy()
  {
    logger.trace("create singleton stage runner proxy bean");

    return stageRunnerProxyBuilder.createProxy(
        (Class<S>)stageFunctionAnnotation.getStageType(),
        stageRunnerInterfaceType,
        stageRunnerInterfaceMethod,
        dataNames,
        (StageRunnerFactory<S>)stageRunnerFactory);
  }


  /**
   * Locates the single abstract (non default) method of the given functional interface and validates its return type.
   * The return type must be either {@code boolean} or {@code void}.
   *
   * @param interfaceType  the candidate functional interface type, not {@code null}
   *
   * @return  the functional interface method, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if the given type is not an interface, is not a functional interface,
   *                                            has no functional method or the method has an unsupported return type
   */
  @Contract(pure = true)
  @SuppressWarnings("ExtractMethodRecommender")
  @NotNull Method findFunctionalInterfaceMethod(@NotNull Class<?> interfaceType)
  {
    if (!interfaceType.isInterface())
      throw new StageRunnerConfigurationException(interfaceType.getName() + " is not an interface");

    Method functionalInterfaceMethod = null;

    for(var method: interfaceType.getDeclaredMethods())
      if (!method.isDefault())
      {
        if (functionalInterfaceMethod != null)
          throw new StageRunnerConfigurationException(interfaceType.getName() + " is not a functional interface");

        functionalInterfaceMethod = method;
      }

    if (functionalInterfaceMethod == null)
      throw new StageRunnerConfigurationException(interfaceType.getName() + " has no functional method");

    var returnType = functionalInterfaceMethod.getReturnType();
    if (returnType != boolean.class && returnType != void.class)
      throw new StageRunnerConfigurationException(functionalInterfaceMethod + " must return boolean or void");

    return functionalInterfaceMethod;
  }


  /**
   * {@inheritDoc}
   * <p>
   * The bean factory is used to inspect bean scopes and to look up optional collaborators such as the
   * {@link ConversionService}.
   *
   * @param beanFactory  the owning bean factory, not {@code null}
   */
  @Override
  @Contract(mutates = "this")
  public void setBeanFactory(@NotNull BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }


  /**
   * Sets the conversion service used to convert data map values to method parameter types. When no service is set
   * explicitly, the unique {@code ConversionService} bean is looked up when the first stage function is registered,
   * with the shared default conversion service as fallback.
   *
   * @param conversionService  conversion service, not {@code null}
   *
   * @throws IllegalArgumentException  if {@code conversionService} is {@code null}
   *
   * @since 0.3.1
   */
  @Contract(mutates = "this")
  public void setConversionService(ConversionService conversionService) {
    this.conversionService = requireNonNull(conversionService, "conversionService must not be null");
  }


  /**
   * Sets the builder used to create the stage runner proxy. When no builder is set explicitly, a default implementation
   * is used.
   *
   * @param stageRunnerProxyBuilder  stage runner proxy builder, not {@code null}
   *
   * @throws IllegalArgumentException  if {@code stageRunnerProxyBuilder} is {@code null}
   */
  @Contract(mutates = "this")
  public void setStageRunnerProxyBuilder(@NotNull StageRunnerProxyBuilder stageRunnerProxyBuilder)
  {
    this.stageRunnerProxyBuilder =
        requireNonNull(stageRunnerProxyBuilder, "stageRunnerProxyBuilder must not be null");
  }


  /**
   * Sets the builder used to turn annotated bean methods into stage functions. When no builder is set explicitly, a
   * default implementation is used.
   *
   * @param stageFunctionBuilder  stage function builder, not {@code null}
   *
   * @throws IllegalArgumentException  if {@code stageFunctionBuilder} is {@code null}
   */
  @Contract(mutates = "this")
  public void setStageFunctionBuilder(@NotNull StageFunctionBuilder stageFunctionBuilder) {
    this.stageFunctionBuilder = requireNonNull(stageFunctionBuilder, "stageFunctionBuilder must not be null");
  }


  /**
   * Sets the filter that decides which discovered stage functions are registered with the stage runner factory. By
   * default all discovered functions are accepted.
   *
   * @param stageFunctionFilter  stage function filter, not {@code null}
   *
   * @throws IllegalArgumentException  if {@code stageFunctionFilter} is {@code null}
   */
  @Contract(mutates = "this")
  public void setStageFunctionFilter(@NotNull StageFunctionFilter stageFunctionFilter) {
    this.stageFunctionFilter = requireNonNull(stageFunctionFilter, "stageFunctionFilter must not be null");
  }


  /**
   * Controls whether annotations declared on the stage runner interface method are copied to the generated proxy
   * method. The setting only applies to the default stage runner proxy builder and is disabled by default.
   *
   * @param copyInterfaceMethodAnnotations  {@code true} to copy interface method annotations, {@code false} otherwise
   *
   * @since 0.3.1
   */
  @Contract(mutates = "this")
  public void setCopyInterfaceMethodAnnotations(boolean copyInterfaceMethodAnnotations) {
    this.copyInterfaceMethodAnnotations = copyInterfaceMethodAnnotations;
  }


  /**
   * Sets the generator which provides the stage function name for an annotated method.
   * <p>
   * By default, the stage function name is provided by the
   * &#x40;{@link de.sayayi.lib.stagerunner.spring.annotation.StageDefinition.Name Name} annotation. Using a stage
   * function name generator the name can be calculated dynamically from the annotation attributes and the method.
   * <p>
   * If the name generator returns {@code null}, the stage function is added as a regular stage function, which is
   * executed for every stage runner execution. Otherwise, it is registered as a named stage function.
   *
   * @param stageFunctionNameGenerator  stage function name generator, not {@code null}
   *
   * @throws IllegalArgumentException  if {@code stageFunctionNameGenerator} is {@code null}
   *
   * @since 0.3.2
   */
  @Contract(mutates = "this")
  public void setStageFunctionNameGenerator(
      @NotNull BiFunction<AnnotationAttributes,Method,String> stageFunctionNameGenerator)
  {
    this.stageFunctionNameGenerator =
        requireNonNull(stageFunctionNameGenerator, "stageFunctionNameGenerator must not be null");
  }
}
