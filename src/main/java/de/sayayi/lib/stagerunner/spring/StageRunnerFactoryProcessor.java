package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.impl.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
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
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;


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



  @SuppressWarnings("unchecked")
  public StageRunnerFactoryProcessor(
      @NotNull Class<R> stageRunnerInterfaceType,
      @NotNull Class<? extends Annotation> stageFunctionAnnotation)
  {
    this.stageRunnerInterfaceType = stageRunnerInterfaceType;
    this.stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotation);
    this.stageRunnerFactory = new DefaultStageRunnerFactory(this.stageFunctionAnnotation.stageType);

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
  }


  @Override
  public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) {
    // not interested in doing anything here
  }


  @Override
  public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName)
      throws BeansException
  {
    try {
      if (beanFactory.isSingleton(beanName))
      {
        // check for annotations
      }
    } catch(NoSuchBeanDefinitionException ignored) {

    }

    return bean;
  }


  @Override
  public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry beanDefinitionRegistry)
      throws BeansException
  {
    final RootBeanDefinition bd = new RootBeanDefinition(stageRunnerInterfaceType,
        SCOPE_SINGLETON, this::createStageRunnerProxy);

    bd.setTargetType(ResolvableType.forClass(stageRunnerInterfaceType));
    bd.setScope(SCOPE_SINGLETON);
    bd.setLazyInit(false);
    bd.setDescription("Auto-detected StageRunner for " + stageFunctionAnnotation.stageType.getName());

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
      throw new IllegalArgumentException(interfaceType.getName() + " is not an interface");

    Method functionalInterfaceMethod = null;

    for(final Method method: interfaceType.getMethods())
      if (!method.isDefault())
      {
        if (functionalInterfaceMethod != null)
          throw new IllegalArgumentException(interfaceType.getName() + " is not a functional interface");

        functionalInterfaceMethod = method;
      }

    if (functionalInterfaceMethod == null)
      throw new IllegalArgumentException(interfaceType.getName() + " has no functional method");

    return functionalInterfaceMethod;
  }


  private void analyseStageRunnerInterfaceMethod()
  {
    final String[] parameterNames = new DefaultParameterNameDiscoverer()
        .getParameterNames(stageRunnerInterfaceMethod);
    final ResolvableType callbackType = ResolvableType.forClassWithGenerics(
        StageRunnerCallback.class, stageFunctionAnnotation.stageType);

    for(int p = 0; p < dataNames.length; p++)
    {
      final MethodParameter methodParameter = new MethodParameter(stageRunnerInterfaceMethod, p);
      final TypeDescriptor parameterType = new TypeDescriptor(methodParameter);
      final ResolvableType resolvableType = parameterType.getResolvableType();

      if (!callbackType.isAssignableFrom(resolvableType))
      {
        final String parameterName = getDataNameForParameter(
            methodParameter.getParameterAnnotation(Data.class), parameterNames, p);

        dataNames[p] = parameterName;
        dataNameTypeMap.put(parameterName, resolvableType);
      }
    }
  }


  private @NotNull String getDataNameForParameter(
      @Nullable Data dataAnnotation, @Nullable String[] parameterNames, int p)
  {
    String parameterName = dataAnnotation != null ? dataAnnotation.value() : "";
    if (parameterName.isEmpty() && parameterNames != null)
      parameterName = parameterNames[p];

    if (parameterName.isEmpty())
    {
      throw new IllegalStateException("Unable to detect data name for parameter " + (p + 1) +
          " in stage runner function " + stageRunnerInterfaceMethod);
    }

    if (dataNameTypeMap.containsKey(parameterName))
    {
      throw new IllegalStateException("Duplicate data name '" + parameterName +
          "' for parameter" + (p + 1) + " in stage runner function " +
          stageRunnerInterfaceMethod);
    }

    return parameterName;
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
