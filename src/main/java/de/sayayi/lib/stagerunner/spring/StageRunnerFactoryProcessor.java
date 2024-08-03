package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.annotation.AbstractStageFunctionBuilder;
import de.sayayi.lib.stagerunner.annotation.Data;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.impl.DefaultStageRunnerFactory;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotationAttributes;
import static org.springframework.util.ClassUtils.isPresent;


@SuppressWarnings("rawtypes")
public class StageRunnerFactoryProcessor<R>
    implements BeanPostProcessor, BeanDefinitionRegistryPostProcessor, BeanFactoryAware, InitializingBean
{
  private final Class<R> stageRunnerInterfaceType;
  private final StageFunctionAnnotation stageFunctionAnnotation;
  private final DefaultStageRunnerFactory stageRunnerFactory;
  private final Method stageRunnerInterfaceMethod;
  private final String[] dataNames;
  private final Map<String,ResolvableType> dataNameTypeMap;

  private BeanFactory beanFactory;
  private ConversionService conversionService;
  private AbstractStageFunctionBuilder stageFunctionBuilder;


  @SuppressWarnings("unchecked")
  public StageRunnerFactoryProcessor(
      @NotNull Class<R> stageRunnerInterfaceType,
      @NotNull Class<? extends Annotation> stageFunctionAnnotation)
  {
    this.stageRunnerInterfaceType = stageRunnerInterfaceType;
    this.stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotation);
    this.stageRunnerFactory = new DefaultStageRunnerFactory(this.stageFunctionAnnotation.getStageType());

    stageRunnerInterfaceMethod = findFunctionalInterfaceMethod(stageRunnerInterfaceType);

    final int parameterCount = stageRunnerInterfaceMethod.getParameterCount();

    dataNames = new String[parameterCount];
    dataNameTypeMap = parameterCount == 0
        ? emptyMap()
        : new HashMap<>((parameterCount * 4 + 2) / 3);
  }


  @Override
  public void afterPropertiesSet()
  {
    if (beanFactory != null && conversionService == null)
    {
      try {
        conversionService = beanFactory.getBean(ConversionService.class);
      } catch(NoSuchBeanDefinitionException ex) {
        conversionService = DefaultConversionService.getSharedInstance();
      }
    }

    analyseStageRunnerInterfaceMethod();

    if (isPresent("net.bytebuddy.ByteBuddy", StageRunnerFactoryProcessor.class.getClassLoader()))
    {
      // preferred, as it produces the fastest stage function implementations
      stageFunctionBuilder = new de.sayayi.lib.stagerunner.annotation.bytebuddy.StageFunctionBuilder(
          stageFunctionAnnotation.getAnnotationType(), conversionService, dataNameTypeMap);
    }
    else
    {
      stageFunctionBuilder = new de.sayayi.lib.stagerunner.annotation.proxy.StageFunctionBuilder(
          stageFunctionAnnotation.getAnnotationType(), conversionService, dataNameTypeMap);
    }
  }


  @Override
  public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) {
    // not interested in doing anything here
  }


  @Override
  public @NotNull Object postProcessAfterInitialization(@NotNull Object bean,
                                                        @NotNull String beanName)
  {
    try {
      if (beanFactory.isSingleton(beanName))
        analyseStageFunctions(bean);
    } catch(NoSuchBeanDefinitionException ignored) {
    }

    return bean;
  }


  @Override
  public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry beanDefinitionRegistry)
  {
    final RootBeanDefinition bd =
        new RootBeanDefinition(stageRunnerInterfaceType, SCOPE_SINGLETON, this::createStageRunnerProxy);

    bd.setTargetType(ResolvableType.forClass(stageRunnerInterfaceType));
    bd.setScope(SCOPE_SINGLETON);
    bd.setLazyInit(false);
    bd.setDescription("Auto-detected StageRunner for " +
        stageFunctionAnnotation.getStageType().getName());

    beanDefinitionRegistry.registerBeanDefinition(stageRunnerInterfaceType.getName(), bd);
  }


  @Contract(pure = true)
  private @NotNull R createStageRunnerProxy()
  {
    //noinspection unchecked
    return (R)Proxy.newProxyInstance(
        stageRunnerInterfaceType.getClassLoader(),
        new Class<?>[] { stageRunnerInterfaceType },
        new StageRunnerInvocationHandler(stageRunnerInterfaceType, stageRunnerFactory, dataNames));
  }


  @Contract(pure = true)
  @NotNull Method findFunctionalInterfaceMethod(@NotNull Class<?> interfaceType)
  {
    if (!interfaceType.isInterface())
      throw new StageRunnerException(interfaceType.getName() + " is not an interface");

    Method functionalInterfaceMethod = null;

    for(final Method method: interfaceType.getMethods())
      if (!method.isDefault())
      {
        if (functionalInterfaceMethod != null)
          throw new StageRunnerException(interfaceType.getName() + " is not a functional interface");

        functionalInterfaceMethod = method;
      }

    if (functionalInterfaceMethod == null)
      throw new StageRunnerException(interfaceType.getName() + " has no functional method");

    return functionalInterfaceMethod;
  }


  private void analyseStageRunnerInterfaceMethod()
  {
    final String[] parameterNames = new DefaultParameterNameDiscoverer()
        .getParameterNames(stageRunnerInterfaceMethod);
    final ResolvableType callbackType = forClassWithGenerics(
        StageRunnerCallback.class, stageFunctionAnnotation.getStageType());
    final Parameter[] parameters = stageRunnerInterfaceMethod.getParameters();

    for(int p = 0; p < dataNames.length; p++)
    {
      final ResolvableType resolvableType = forMethodParameter(stageRunnerInterfaceMethod, p);

      if (!callbackType.isAssignableFrom(resolvableType))
      {
        final String parameterName = getDataNameForParameter(
            parameters[p].getAnnotation(Data.class), parameterNames, p);

        dataNames[p] = parameterName;
        dataNameTypeMap.put(parameterName, resolvableType);
      }
    }
  }


  private @NotNull String getDataNameForParameter(
      @Nullable Data dataAnnotation, @Nullable String[] parameterNames, int p)
  {
    String parameterName = dataAnnotation != null ? dataAnnotation.name() : "";
    if (parameterName.isEmpty() && parameterNames != null)
      parameterName = parameterNames[p];

    if (parameterName.isEmpty())
    {
      throw new StageRunnerException("unable to detect data name for parameter " + (p + 1) +
          " in stage runner function " + stageRunnerInterfaceMethod);
    }

    if (dataNameTypeMap.containsKey(parameterName))
    {
      throw new StageRunnerException("duplicate data name '" + parameterName +
          "' for parameter" + (p + 1) + " in stage runner function " +
          stageRunnerInterfaceMethod);
    }

    return parameterName;
  }


  private void analyseStageFunctions(@NotNull Object bean)
  {
    final Class<?> beanType = bean.getClass();
    final Class<? extends Annotation> annotationType = stageFunctionAnnotation.getAnnotationType();

    for(final Method method: beanType.getMethods())
    {
      final AnnotationAttributes stageFunctionAnnotationAttributes =
          findMergedAnnotationAttributes(method, annotationType, false, false);

      if (stageFunctionAnnotationAttributes != null)
      {
        //noinspection unchecked
        stageRunnerFactory.addStageFunction(
            stageFunctionAnnotationAttributes.getEnum(stageFunctionAnnotation.getStageProperty()),
            stageFunctionAnnotationAttributes.getNumber(stageFunctionAnnotation.getOrderProperty()).intValue(),
            stageFunctionAnnotationAttributes.getString(stageFunctionAnnotation.getDescriptionProperty()),
            createStageFunction(bean, method));
      }
    }
  }


  private <S extends Enum<S>> @NotNull StageFunction<S> createStageFunction(
      @NotNull Object bean, @NotNull Method stageFunction)
  {
    try {
      return stageFunctionBuilder.buildFor(bean, stageFunction);
    } catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }


  @Override
  public void setBeanFactory(@NotNull BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }


  @SuppressWarnings("unused")
  public void setConversionService(@NotNull ConversionService conversionService) {
    this.conversionService = requireNonNull(conversionService);
  }
}
