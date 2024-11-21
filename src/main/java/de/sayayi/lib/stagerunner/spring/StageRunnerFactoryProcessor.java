package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import de.sayayi.lib.stagerunner.spring.builder.AbstractStageFunctionBuilder;
import de.sayayi.lib.stagerunner.spring.builder.StageFunctionBuilderImpl;
import de.sayayi.lib.stagerunner.spring.builder.StageRunnerProxyBuilderImpl;
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
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
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
  private final Class<R> stageRunnerInterfaceType;
  private final StageFunctionAnnotation stageFunctionAnnotation;
  private final DefaultStageRunnerFactory stageRunnerFactory;
  private final Method stageRunnerInterfaceMethod;
  private final String[] dataNames;
  private final Map<String,ResolvableType> dataNameTypeMap;

  private BeanFactory beanFactory;
  private ConversionService conversionService;
  private AbstractStageFunctionBuilder stageFunctionBuilder;
  private StageRunnerProxyBuilder stageRunnerProxyBuilder;


  @SuppressWarnings("unchecked")
  public StageRunnerFactoryProcessor(@NotNull Class<R> stageRunnerInterfaceType,
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


  public void setStageRunnerProxyBuilder(@NotNull StageRunnerProxyBuilder stageRunnerProxyBuilder) {
    this.stageRunnerProxyBuilder = stageRunnerProxyBuilder;
  }


  @Override
  public void afterPropertiesSet()
  {
    if (conversionService == null)
    {
      try {
        conversionService = beanFactory.getBean(ConversionService.class);
      } catch(NullPointerException | NoSuchBeanDefinitionException ex) {
        conversionService = DefaultConversionService.getSharedInstance();
      }
    }

    analyseStageRunnerInterfaceMethod();

    if (stageRunnerProxyBuilder == null)
      setStageRunnerProxyBuilder(new StageRunnerProxyBuilderImpl(false));

    stageFunctionBuilder = new StageFunctionBuilderImpl(
        stageFunctionAnnotation.getAnnotationType(), conversionService, dataNameTypeMap);
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


  @Override
  public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry beanDefinitionRegistry)
  {
    var bd = new RootBeanDefinition(stageRunnerInterfaceType, SCOPE_SINGLETON, this::createStageRunnerProxy);

    bd.setTargetType(ResolvableType.forClass(stageRunnerInterfaceType));
    bd.setLazyInit(false);
    bd.setDescription("Auto-detected StageRunner for " + stageFunctionAnnotation.getStageType().getName());

    beanDefinitionRegistry.registerBeanDefinition(stageRunnerInterfaceType.getName(), bd);
  }


  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  private <S extends Enum<S>> @NotNull R createStageRunnerProxy()
  {
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


  private void analyseStageRunnerInterfaceMethod()
  {
    var parameterNames = new DefaultParameterNameDiscoverer().getParameterNames(stageRunnerInterfaceMethod);
    var callbackType = forClassWithGenerics(StageRunnerCallback.class, stageFunctionAnnotation.getStageType());
    var parameters = stageRunnerInterfaceMethod.getParameters();
    ResolvableType resolvableType;

    for(int p = 0; p < dataNames.length; p++)
      if (!callbackType.isAssignableFrom(resolvableType = forMethodParameter(stageRunnerInterfaceMethod, p)))
      {
        dataNameTypeMap.put(
            dataNames[p] = getDataNameForParameter(findMergedAnnotation(parameters[p], Data.class), parameterNames, p),
            resolvableType);
      }
  }


  @Contract(pure = true)
  private @NotNull String getDataNameForParameter(@Nullable Data dataAnnotation,
                                                  @Nullable String[] parameterNames,
                                                  int p)
  {
    var parameterName = dataAnnotation != null ? dataAnnotation.name() : "";
    if (parameterName.isEmpty() && parameterNames != null)
      parameterName = parameterNames[p];

    if (parameterName == null || parameterName.isEmpty())
    {
      throw new StageRunnerException("unable to detect data name for parameter " + (p + 1) +
          " in stage runner function " + stageRunnerInterfaceMethod);
    }

    if (dataNameTypeMap.containsKey(parameterName))
    {
      throw new StageRunnerException("duplicate data name '" + parameterName +
          "' for parameter" + (p + 1) + " in stage runner function " + stageRunnerInterfaceMethod);
    }

    return parameterName;
  }


  @SuppressWarnings("unchecked")
  private void analyseStageFunctions(@NotNull Object bean)
  {
    var beanType = bean.getClass();
    var annotationType = stageFunctionAnnotation.getAnnotationType();

    for(var method: beanType.getMethods())
    {
      var stageFunctionAnnotationAttributes =
          findMergedAnnotationAttributes(method, annotationType, false, false);

      if (stageFunctionAnnotationAttributes != null)
      {
        var stageEnum = stageFunctionAnnotation.getStage(stageFunctionAnnotationAttributes);
        var order = stageFunctionAnnotation.getOrder(stageFunctionAnnotationAttributes);
        var description = stageFunctionAnnotation.getDescription(stageFunctionAnnotationAttributes);
        var function = createStageFunction(bean, method);

        var name = stageFunctionAnnotation.getName(stageFunctionAnnotationAttributes);
        if (name != null)
          stageRunnerFactory.namedStageFunction(name, stageEnum, order, description, function);
        else
          stageRunnerFactory.addStageFunction(stageEnum, order, description, function);
      }
    }
  }


  private <S extends Enum<S>> @NotNull StageFunction<S> createStageFunction(@NotNull Object bean,
                                                                            @NotNull Method stageFunction)
  {
    try {
      return stageFunctionBuilder.buildFor(bean, stageFunction);
    } catch(StageRunnerConfigurationException ex) {
      throw ex;
    } catch(Exception ex) {
      throw new StageRunnerConfigurationException(ex.getMessage(), ex);
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
