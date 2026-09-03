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
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotationAttributes;


/**
 * Spring post processor that binds a user defined stage runner interface to the beans in the application context.
 * <p>
 * On startup this processor registers a singleton bean implementing the stage runner interface and inspects every
 * other singleton bean for methods carrying the configured stage function annotation. Matching methods are turned
 * into stage functions and registered with an internal {@link StageRunnerFactory} which the proxy delegates to.
 * <p>
 * The behavior can be customized by supplying a {@link StageFunctionFilter}, a {@link StageFunctionBuilder}, a
 * {@link StageRunnerProxyBuilder}, a {@link ConversionService} or a stage function name generator through the
 * various setter methods.
 *
 * @param <R>  stage runner interface type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@SuppressWarnings("rawtypes")
public class StageRunnerFactoryProcessor<R>
    implements BeanPostProcessor, BeanDefinitionRegistryPostProcessor, BeanFactoryAware, InitializingBean
{
  protected final Log logger = LogFactory.getLog(StageRunnerFactoryProcessor.class);

  protected final Class<R> stageRunnerInterfaceType;
  protected final StageFunctionAnnotation stageFunctionAnnotation;
  protected final DefaultStageRunnerFactory stageRunnerFactory;
  protected final Method stageRunnerInterfaceMethod;
  protected final String[] dataNames;
  protected final Map<String,ResolvableType> dataNameTypeMap;

  protected BeanFactory beanFactory;
  protected ConversionService conversionService;

  protected StageRunnerProxyBuilder stageRunnerProxyBuilder;
  protected StageFunctionBuilder stageFunctionBuilder;
  protected boolean copyInterfaceMethodAnnotations;
  protected BiFunction<AnnotationAttributes,Method,String> stageFunctionNameGenerator;

  protected StageFunctionFilter stageFunctionFilter = new StageFunctionFilter() {
    @Override
    public <B,S extends Enum<S>> boolean filter(@NotNull B bean, @NotNull S stage, int order, String name) {
      return true;
    }
  };


  /**
   * Create a stage runner factory processor for the given stage runner interface and stage function annotation type.
   *
   * @param stageRunnerInterfaceType      stage runner interface type, not {@code null}
   * @param stageFunctionAnnotationType   stage function annotation type, not {@code null}
   */
  @SuppressWarnings("unchecked")
  public StageRunnerFactoryProcessor(@NotNull Class<R> stageRunnerInterfaceType,
                                     @NotNull Class<? extends Annotation> stageFunctionAnnotationType)
  {
    var stageType =
        (stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotationType)).getStageType();
    logger.debug("stage type = " + stageType);

    this.stageRunnerFactory = new DefaultStageRunnerFactory(stageType);
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
   * Initializes the processor after all properties have been set. Missing collaborators are supplied with default
   * implementations: a default {@link StageRunnerProxyBuilder} and, if needed, a default {@link StageFunctionBuilder}
   * backed by the configured or discovered {@link ConversionService}.
   */
  @Override
  public void afterPropertiesSet()
  {
    if (stageRunnerProxyBuilder == null)
    {
      logger.trace("set default stage runner proxy builder");
      setStageRunnerProxyBuilder(new StageRunnerProxyBuilderImpl(copyInterfaceMethodAnnotations));
    }

    if (stageFunctionBuilder == null)
    {
      if (conversionService == null)
      {
        try {
          conversionService = beanFactory.getBean(ConversionService.class);
        } catch(NoSuchBeanDefinitionException ex) {
          logger.warn("could not find ConversionService bean - use default conversion service", ex);
          conversionService = DefaultConversionService.getSharedInstance();
        }
      }

      logger.trace("set default stage function builder");
      setStageFunctionBuilder(new StageFunctionBuilderImpl(conversionService));
    }
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
   * Analyses singleton beans after initialization and registers any methods carrying the stage function annotation
   * with the internal stage runner factory. Non singleton beans and beans without a known definition are skipped.
   *
   * @param bean      the initialized bean, not {@code null}
   * @param beanName  the bean name, not {@code null}
   *
   * @return  the bean instance unchanged, never {@code null}
   */
  @Override
  public @NotNull Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName)
  {
    try {
      if (beanFactory.isSingleton(beanName))
        analyseStageFunctions(bean);
    } catch(NoSuchBeanDefinitionException ignored) {
    }

    return bean;
  }


  /**
   * Scans the given bean for methods carrying the configured stage function annotation and registers each match.
   *
   * @param bean  the bean to inspect, not {@code null}
   */
  @SuppressWarnings("DataFlowIssue")
  protected void analyseStageFunctions(@NotNull Object bean)
  {
    var annotationType = stageFunctionAnnotation.getAnnotationType();

    for(var method: ultimateTargetClass(bean).getMethods())
      if (method.isAnnotationPresent(annotationType))
      {
        registerStageFunction(
            findMergedAnnotationAttributes(method, annotationType, false, false),
            method, bean);
      }
  }


  /**
   * Registers a single annotated method as a stage function on the internal stage runner factory. Registration is
   * skipped when the configured {@link StageFunctionFilter} rejects the function. If a name is provided by the name
   * generator the function is registered as a named function, otherwise it is added anonymously.
   *
   * @param stageFunctionAnnotationAttributes  merged attributes of the stage function annotation, not {@code null}
   * @param method                             the annotated method, not {@code null}
   * @param bean                               the bean the method belongs to, not {@code null}
   */
  @SuppressWarnings("unchecked")
  protected void registerStageFunction(@NotNull AnnotationAttributes stageFunctionAnnotationAttributes,
                                       @NotNull Method method,
                                       @NotNull Object bean)
  {
    var stageEnum = stageFunctionAnnotation.getStage(stageFunctionAnnotationAttributes);
    var order = stageFunctionAnnotation.getOrder(stageFunctionAnnotationAttributes);
    var name = stageFunctionNameGenerator.apply(stageFunctionAnnotationAttributes, method);

    if (stageFunctionFilter.filter(bean, (Enum)stageEnum, order, name))
    {
      var description = stageFunctionAnnotation.getDescription(stageFunctionAnnotationAttributes);
      var function = stageFunctionBuilder
          .createStageFunction(stageFunctionAnnotation, dataNameTypeMap, method, bean);

      if (logger.isDebugEnabled())
      {
        var msg = new StringBuilder();

        if (name == null)
          msg.append("add stage function");
        else
          msg.append("register named stage function '").append(name).append("'");

        msg.append(", stage {}").append(stageEnum).append('#').append(order);

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
   * Registers a lazily initialized singleton bean definition for the stage runner interface. The bean is created
   * through {@link #createStageRunnerProxy()} on first access.
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
   * Locates the single abstract (non default) method of the given functional interface and validates its return
   * type. The return type must be either {@code boolean} or {@code void}.
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
   * Stores the bean factory that owns this processor. It is used to inspect bean scopes and to look up optional
   * collaborators such as the {@link ConversionService}.
   *
   * @param beanFactory  the owning bean factory, not {@code null}
   */
  @Override
  public void setBeanFactory(@NotNull BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }


  /**
   * Sets the conversion service used to convert data map values to method parameter types. When no service is set
   * explicitly, one is looked up in the bean factory or the shared default conversion service is used as fallback.
   *
   * @param conversionService  conversion service, not {@code null}
   */
  public void setConversionService(ConversionService conversionService)
  {
    Assert.notNull(conversionService, "conversionService must not be null");
    this.conversionService = conversionService;
  }


  /**
   * Sets the builder used to create the stage runner proxy. When no builder is set explicitly, a default
   * implementation is used.
   *
   * @param stageRunnerProxyBuilder  stage runner proxy builder, not {@code null}
   */
  public void setStageRunnerProxyBuilder(@NotNull StageRunnerProxyBuilder stageRunnerProxyBuilder)
  {
    Assert.notNull(stageRunnerProxyBuilder, "stageRunnerProxyBuilder must not be null");
    this.stageRunnerProxyBuilder = stageRunnerProxyBuilder;
  }


  /**
   * Sets the builder used to turn annotated bean methods into stage functions. When no builder is set explicitly,
   * a default implementation is used.
   *
   * @param stageFunctionBuilder  stage function builder, not {@code null}
   */
  public void setStageFunctionBuilder(@NotNull StageFunctionBuilder stageFunctionBuilder)
  {
    Assert.notNull(stageFunctionBuilder, "stageFunctionBuilder must not be null");
    this.stageFunctionBuilder = stageFunctionBuilder;
  }


  /**
   * Sets the filter that decides which discovered stage functions are registered with the stage runner factory. By
   * default all discovered functions are accepted.
   *
   * @param stageFunctionFilter  stage function filter, not {@code null}
   */
  public void setStageFunctionFilter(@NotNull StageFunctionFilter stageFunctionFilter)
  {
    Assert.notNull(stageFunctionFilter, "stageFunctionFilter must not be null");
    this.stageFunctionFilter = stageFunctionFilter;
  }


  /**
   * Controls whether annotations declared on the stage runner interface method are copied to the generated proxy
   * method.
   *
   * @param copyInterfaceMethodAnnotations  {@code true} to copy interface method annotations, {@code false}
   *                                        otherwise
   */
  public void setCopyInterfaceMethodAnnotations(boolean copyInterfaceMethodAnnotations) {
    this.copyInterfaceMethodAnnotations = copyInterfaceMethodAnnotations;
  }


  /**
   * By default, the stage function name is provided by the
   * &#x40;{@link de.sayayi.lib.stagerunner.spring.annotation.StageDefinition.Name Name} annotation. Using a stage
   * function name generator the name can be calculated dynamically.
   * <p>
   * If the name generator returns {@code null} the stage function will be added immediately to the stage runner
   * factory.
   *
   * @param stageFunctionNameGenerator  stage function name generator, not {@code null}
   *
   * @since 0.3.2
   */
  public void setStageFunctionNameGenerator(
      @NotNull BiFunction<AnnotationAttributes,Method,String> stageFunctionNameGenerator)
  {
    Assert.notNull(stageFunctionNameGenerator, "stageFunctionNameGenerator must not be null");
    this.stageFunctionNameGenerator = stageFunctionNameGenerator;
  }
}
