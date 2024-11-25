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
   * @param stageRunnerInterfaceType  stage runner interface type, not {@code null}
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
          logger.trace("could not find ConversionService bean - use default conversion service", ex);
          conversionService = DefaultConversionService.getSharedInstance();
        }
      }

      logger.trace("set default stage function builder");
      setStageFunctionBuilder(new StageFunctionBuilderImpl(conversionService));
    }
  }


  @Override
  public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) {
    // not interested in doing anything here
  }


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


  protected void analyseStageFunctions(@NotNull Object bean)
  {
    var annotationType = stageFunctionAnnotation.getAnnotationType();

    for(var method: ultimateTargetClass(bean).getMethods())
    {
      var stageFunctionAnnotationAttributes =
          findMergedAnnotationAttributes(method, annotationType, false, false);

      if (stageFunctionAnnotationAttributes != null)
        registerStageFunction(stageFunctionAnnotationAttributes, method, bean);
    }
  }


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
        logger.debug("add stage function" + (name == null ? "" : " '" + name + "'") + ", stage " +
            stageEnum + '#' + order + ((description == null) ? "" : ", description '" + description + "'") +
            ": " + function);
      }

      if (name != null)
        stageRunnerFactory.namedStageFunction(name, stageEnum, order, description, function);
      else
        stageRunnerFactory.addStageFunction(stageEnum, order, description, function);
    }
  }


  @Override
  public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry beanDefinitionRegistry)
  {
    var bean = new RootBeanDefinition(stageRunnerInterfaceType, SCOPE_SINGLETON, this::createStageRunnerProxy);

    bean.setTargetType(ResolvableType.forClass(stageRunnerInterfaceType));
    bean.setLazyInit(true);
    bean.setDescription("Auto-detected StageRunner for " + stageFunctionAnnotation.getStageType().getName());
    bean.setPrimary(true);

    if (logger.isDebugEnabled())
      logger.trace("register singleton bean: " + stageRunnerInterfaceType.getName());

    beanDefinitionRegistry.registerBeanDefinition(stageRunnerInterfaceType.getName(), bean);
  }


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


  @Override
  public void setBeanFactory(@NotNull BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }


  public void setConversionService(ConversionService conversionService)
  {
    Assert.notNull(conversionService, "conversionService must not be null");
    this.conversionService = conversionService;
  }


  public void setStageRunnerProxyBuilder(@NotNull StageRunnerProxyBuilder stageRunnerProxyBuilder)
  {
    Assert.notNull(stageRunnerProxyBuilder, "stageRunnerProxyBuilder must not be null");
    this.stageRunnerProxyBuilder = stageRunnerProxyBuilder;
  }


  public void setStageFunctionBuilder(@NotNull StageFunctionBuilder stageFunctionBuilder)
  {
    Assert.notNull(stageFunctionBuilder, "stageFunctionBuilder must not be null");
    this.stageFunctionBuilder = stageFunctionBuilder;
  }


  public void setStageFunctionFilter(@NotNull StageFunctionFilter stageFunctionFilter)
  {
    Assert.notNull(stageFunctionFilter, "stageFunctionFilter must not be null");
    this.stageFunctionFilter = stageFunctionFilter;
  }


  public void setCopyInterfaceMethodAnnotations(boolean copyInterfaceMethodAnnotations) {
    this.copyInterfaceMethodAnnotations = copyInterfaceMethodAnnotations;
  }


  public void setStageFunctionNameGenerator(
      @NotNull BiFunction<AnnotationAttributes,Method,String> stageFunctionNameGenerator)
  {
    Assert.notNull(stageFunctionNameGenerator, "stageFunctionNameGenerator must not be null");
    this.stageFunctionNameGenerator = stageFunctionNameGenerator;
  }
}
