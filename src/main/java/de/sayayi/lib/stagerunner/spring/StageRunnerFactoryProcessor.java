package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
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
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.util.ReflectionUtils.invokeMethod;
import static org.springframework.util.StringUtils.hasLength;


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
        analyseStageFunctions(bean);
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
    final ResolvableType callbackType = forClassWithGenerics(
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


  private void analyseStageFunctions(@NotNull Object singletonBean)
  {
    final Class<?> beanType = singletonBean.getClass();
    final Class<? extends Annotation> annotationType = stageFunctionAnnotation.annotationType;
    final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    for(final Method method: beanType.getMethods())
    {
      final AnnotationAttributes stageFunctionAnnotationAttributes = AnnotatedElementUtils
          .findMergedAnnotationAttributes(method, annotationType, false, false);

      if (stageFunctionAnnotationAttributes != null)
      {
        //noinspection unchecked
        stageRunnerFactory.addStageFunction(
            stageFunctionAnnotationAttributes.getEnum(stageFunctionAnnotation.stageProperty),
            stageFunctionAnnotationAttributes.getString(stageFunctionAnnotation.descriptionProperty),
            createStageFunction(singletonBean, method, parameterNameDiscoverer),
            stageFunctionAnnotationAttributes.getNumber(stageFunctionAnnotation.orderProperty).intValue());
      }
    }
  }


  private <S extends Enum<S>> @NotNull StageFunction<S> createStageFunction(
      @NotNull Object singletonBean, @NotNull Method stageFunction,
      @NotNull ParameterNameDiscoverer parameterNameDiscoverer)
  {
    final int parameterCount = stageFunction.getParameterCount();
    final Object target = Modifier.isStatic(stageFunction.getModifiers()) ? null : singletonBean;

    //noinspection unchecked
    final Function<StageContext<S>,Object>[] parameterFunctions = new Function[parameterCount];
    final ResolvableType stageContextType =
        forClassWithGenerics(StageContext.class, stageFunctionAnnotation.stageType);
    final String[] parameterNames = parameterNameDiscoverer.getParameterNames(stageFunction);

    for(int p = 0; p < parameterCount; p++)
    {
      final MethodParameter methodParameter = new MethodParameter(stageFunction, p);
      final TypeDescriptor parameterType = new TypeDescriptor(methodParameter);
      final ResolvableType resolvableType = parameterType.getResolvableType();

      if (resolvableType.isAssignableFrom(stageContextType))
        parameterFunctions[p] = this::parameterStageContext;
      else
      {
        final String dataName = findDataNameForStageFunctionParameter(
            methodParameter.getParameterAnnotation(Data.class), resolvableType,
            parameterNames == null ? null : parameterNames[p]);

        if (dataName == null)
          parameterFunctions[p] = ctx -> parameterNull();
        else
          parameterFunctions[p] = ctx -> parameterStageData(ctx, dataName, parameterType);
      }
    }

    return stageContext -> invokeMethod(stageFunction, target,
        Arrays.stream(parameterFunctions).map(f -> f.apply(stageContext)).toArray());
  }


  @Contract(pure = true)
  private String findDataNameForStageFunctionParameter(@Nullable Data dataAnnotation,
                                                       @NotNull ResolvableType parameterType,
                                                       String parameterName)
  {
    String dataName = dataAnnotation != null ? dataAnnotation.value() : null;
    if (!hasLength(dataName) && hasLength(parameterName))
      dataName = parameterName;

    if (dataName == null)
    {
      // lookup by type
      final List<String> candidatesLenient = dataNameTypeMap
          .entrySet()
          .stream()
          .filter(e -> parameterType.isAssignableFrom(e.getValue()))
          .map(Map.Entry::getKey)
          .collect(toList());

      if (candidatesLenient.size() == 1)
        dataName = candidatesLenient.get(0);
      else if (!candidatesLenient.isEmpty())
      {
        final List<String> candidatesEqual = dataNameTypeMap
            .entrySet()
            .stream()
            .filter(e -> parameterType.equals(e.getValue()))
            .map(Map.Entry::getKey)
            .collect(toList());

        if (candidatesEqual.size() == 1)
          dataName = candidatesEqual.get(0);
      }
    }

    if (dataName != null)
    {

    }

    return dataName;
  }


  @Contract(pure = true)
  private @NotNull Object parameterStageContext(@NotNull StageContext<?> stageContext) {
    return stageContext;
  }


  @Contract(pure = true)
  private Object parameterStageData(@NotNull StageContext<?> stageContext,
                                    @NotNull String dataName,
                                    @NotNull TypeDescriptor parameterType)
  {
    final Object data = stageContext.getData(dataName);
    return conversionService.convert(data, TypeDescriptor.forObject(data), parameterType);
  }


  @Contract(pure = true)
  private Object parameterNull() {
    return null;
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
