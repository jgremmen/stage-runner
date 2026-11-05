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

import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.StageRunnerParameter.CallbackParameter;
import de.sayayi.lib.stagerunner.spring.StageRunnerParameter.DataParameter;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import de.sayayi.lib.stagerunner.spring.builder.StageFunctionBuilderImpl;
import de.sayayi.lib.stagerunner.spring.builder.StageRunnerProxyBuilderImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.target.AbstractBeanFactoryBasedTargetSource;
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
import java.util.*;
import java.util.function.BiFunction;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Collections.newSetFromMap;
import static java.util.Objects.requireNonNull;
import static org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass;
import static org.springframework.aop.framework.autoproxy.AutoProxyUtils.determineTargetClass;
import static org.springframework.aop.scope.ScopedProxyUtils.getTargetBeanName;
import static org.springframework.aop.scope.ScopedProxyUtils.isScopedTarget;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.core.ResolvableType.forMethodParameter;
import static org.springframework.core.annotation.AnnotatedElementUtils.*;
import static org.springframework.core.annotation.AnnotationUtils.isCandidateClass;
import static org.springframework.util.ReflectionUtils.isObjectMethod;
import static org.springframework.util.StringUtils.hasLength;


/**
 * Spring post processor that binds a user defined stage runner interface to the beans in the application context.
 * <p>
 * On startup this processor registers a singleton bean implementing the stage runner interface. Once all non-lazy
 * singletons have been instantiated, it inspects every singleton bean for methods carrying the configured stage
 * function annotation. Matching methods are turned into stage functions and registered with an internal
 * {@link StageRunnerFactory} which the proxy delegates to. Only public methods, including inherited ones, are
 * considered. The annotation may also be inherited from an overridden or implemented method or be present as a
 * meta-annotation of a composed annotation (see {@link #isStageFunction(Method)}). Lazy-init singletons whose declared
 * type has stage functions are created at that point; other lazy-init singletons are not, unless
 * {@link #setCreateLazySingletons(boolean) setCreateLazySingletons} is enabled. The stage runner cannot be run before
 * the stage functions have been discovered (see {@link #afterSingletonsInstantiated()}).
 * <p>
 * The behavior can be customized by supplying a {@link StageFunctionFilter}, a {@link StageFunctionBuilder}, a
 * {@link StageRunnerProxyBuilder}, a {@link ConversionService} or a stage function name generator through the various
 * setter methods.
 * <p>
 * The functional method of the stage runner interface must return {@code boolean} or {@code void}. Its parameters are
 * passed to the stage runner as data, named by the {@link Data @Data} annotation or the parameter name. A parameter of
 * type {@link StageRunnerCallback} (or a subtype) is not passed as data but used as callback for the execution. At most
 * one callback parameter is allowed; it must not be annotated with {@code @Data} and its stage type, if specified, must
 * match the stage type of the stage function annotation. If the callback is {@code null}, the
 * {@link StageRunnerCallback#defaultCallback() default callback} is used. The binding of stage function parameters is
 * described by {@link Data @Data}.
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
public class StageRunnerFactoryProcessor<R>
    implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware, InitializingBean, SmartInitializingSingleton
{
  /** Logger used by this processor. */
  private final Log logger = LogFactory.getLog(StageRunnerFactoryProcessor.class);

  /** Stage runner interface implemented by the registered proxy bean. */
  private final Class<R> stageRunnerInterfaceType;

  /** Descriptor of the stage function annotation marking the bean methods to register. */
  private final StageFunctionAnnotation stageFunctionAnnotation;

  /** Stage runner factory populated with the discovered stage functions and used by the proxy. */
  private final DefaultStageRunnerFactory<?> stageRunnerFactory;

  /** Functional method of the stage runner interface. */
  private final Method stageRunnerInterfaceMethod;

  /**
   * Unmodifiable list describing how each parameter of {@link #stageRunnerInterfaceMethod} is passed to the stage
   * runner, indexed by parameter position.
   */
  private final List<StageRunnerParameter> stageRunnerParameters;

  /** Unmodifiable mapping of data names to the types of the corresponding interface method parameters. */
  private final Map<String,ResolvableType> dataNameTypeMap;

  /** Bean factory owning this processor. */
  private BeanFactory beanFactory;

  /** Conversion service used by the default stage function builder, or {@code null} if not yet determined. */
  private ConversionService conversionService;

  /** Builder for the stage runner proxy, or {@code null} to use the default implementation. */
  private StageRunnerProxyBuilder stageRunnerProxyBuilder;

  /**
   * Builder for stage functions, or {@code null} to use the default implementation. Access it through
   * {@link #getStageFunctionBuilder()}, which creates the default implementation on first use.
   */
  private StageFunctionBuilder stageFunctionBuilder;

  /**
   * Whether annotations of the interface method are copied to the generated proxy method.
   *
   * @since 0.3.1
   */
  private boolean copyInterfaceMethodAnnotations;

  /**
   * Generator providing the stage function name for an annotated method. The generator returns {@code null} for a
   * regular (unnamed) stage function.
   *
   * @since 0.3.2
   */
  private BiFunction<AnnotationAttributes,Method,String> stageFunctionNameGenerator;

  /**
   * Whether the stage functions of all singleton beans have been discovered and registered.
   *
   * @since 0.4.0
   */
  private volatile boolean stageFunctionsDiscovered;

  /**
   * Whether singletons which have not been created yet are created during discovery, regardless of their declared
   * type.
   *
   * @since 0.4.1
   */
  private boolean createLazySingletons;

  /**
   * Static stage function methods which have already been analyzed. A static method is exposed by every bean whose
   * class declares or inherits it, but must be registered only once.
   *
   * @since 0.4.0
   */
  private final Set<Method> analysedStaticStageFunctions = new HashSet<>();

  /**
   * Bean instances which have already been analyzed, compared by identity. A bean instance exposed by more than one
   * bean name is analyzed only once.
   *
   * @since 0.4.1
   */
  private final Set<Object> analysedBeans = newSetFromMap(new IdentityHashMap<>());

  /** Filter deciding which discovered stage functions are registered; accepts all functions by default. */
  private StageFunctionFilter stageFunctionFilter = (bean, stage, order, name) -> true;


  /**
   * Creates a stage runner factory processor for the given stage runner interface and stage function annotation type.
   *
   * @param stageRunnerInterfaceType      stage runner interface type, not {@code null}
   * @param stageFunctionAnnotationType   stage function annotation type, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage function annotation is malformed, the stage runner
   *                                            interface is not a valid functional interface, one of its method
   *                                            parameters is annotated with {@link Data @Data} without name, the
   *                                            callback parameter is annotated with {@code @Data} or declares a
   *                                            different stage type, more than one callback parameter is declared, or a
   *                                            data name cannot be determined or is used for more than one parameter
   */
  public StageRunnerFactoryProcessor(@NotNull Class<R> stageRunnerInterfaceType,
                                     @NotNull Class<? extends Annotation> stageFunctionAnnotationType)
  {
    var stageType =
        (stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotationType)).getStageType();
    logger.debug("stage type = " + stageType);

    this.stageRunnerInterfaceType = stageRunnerInterfaceType;
    this.stageRunnerFactory = createStageRunnerFactory(stageType);
    this.stageRunnerInterfaceMethod = findFunctionalInterfaceMethod(stageRunnerInterfaceType);
    logger.debug("stage runner interface method = " + stageRunnerInterfaceMethod);

    final var parameters = stageRunnerInterfaceMethod.getParameters();
    final var parameterCount = parameters.length;

    final var tmpStageRunnerParameters = new ArrayList<StageRunnerParameter>(parameterCount);
    final var tmpDataNameTypeMap = new HashMap<String,ResolvableType>();
    final var callbackType = forClassWithGenerics(StageRunnerCallback.class, stageType);
    final var parameterNames = new DefaultParameterNameDiscoverer().getParameterNames(stageRunnerInterfaceMethod);
    var callbackParameterFound = false;

    for(var p = 0; p < parameterCount; p++)
    {
      final var dataAnnotation = findMergedAnnotation(parameters[p], Data.class);
      final var resolvableType = forMethodParameter(stageRunnerInterfaceMethod, p);

      // the callback is identified by its erased type, consistent with the proxy builder
      if (StageRunnerCallback.class.isAssignableFrom(parameters[p].getType()))
      {
        if (dataAnnotation != null)
        {
          throw new StageRunnerConfigurationException("@Data is not allowed for callback parameter " + (p + 1) +
              " in stage runner function " + stageRunnerInterfaceMethod);
        }

        if (callbackParameterFound)
        {
          throw new StageRunnerConfigurationException("duplicate callback parameter " + (p + 1) +
              " in stage runner function " + stageRunnerInterfaceMethod);
        }

        // a raw or wildcard typed callback is accepted, a callback for a different stage type is not
        if (!callbackType.isAssignableFrom(resolvableType) &&
            !resolvableType.as(StageRunnerCallback.class).hasUnresolvableGenerics())
        {
          throw new StageRunnerConfigurationException("callback parameter " + (p + 1) + " in stage runner function " +
              stageRunnerInterfaceMethod + " must be a " + callbackType);
        }

        tmpStageRunnerParameters.add(new CallbackParameter());
        callbackParameterFound = true;
      }
      else
      {
        final var dataName = getDataNameForParameter(dataAnnotation, parameterNames, p);

        if (tmpDataNameTypeMap.put(dataName, resolvableType) != null)
        {
          throw new StageRunnerConfigurationException("duplicate data name '" + dataName + "' for parameter #" +
              (p + 1) + " in stage runner function " + stageRunnerInterfaceMethod);
        }

        tmpStageRunnerParameters.add(new DataParameter(dataName));
      }
    }

    stageRunnerParameters = List.copyOf(tmpStageRunnerParameters);
    dataNameTypeMap = Map.copyOf(tmpDataNameTypeMap);
    logger.debug("stage runner data = " + dataNameTypeMap);

    stageFunctionNameGenerator = (stageFunctionAnnotationAttributes,method) ->
        stageFunctionAnnotation.getName(stageFunctionAnnotationAttributes);
  }


  /**
   * Creates the internal stage runner factory. Its runners can only be created after the stage functions of all
   * singleton beans have been discovered.
   *
   * @param stageType  stage enumeration type, not {@code null}
   *
   * @return  stage runner factory, never {@code null}
   *
   * @param <S>  stage enumeration type
   *
   * @since 0.4.0
   */
  @SuppressWarnings("unchecked")
  private <S extends Enum<S>> @NotNull DefaultStageRunnerFactory<S> createStageRunnerFactory(
      @NotNull Class<?> stageType)
  {
    return new DefaultStageRunnerFactory<>((Class<S>)stageType) {
      @Override
      public @NotNull StageRunner<S> createRunner()
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
  }


  /**
   * Resolves the data name for a parameter of the stage runner interface method. The name is taken from the
   * {@link Data @Data} annotation when present, otherwise the reflected parameter name is used.
   * <p>
   * A {@code @Data} annotation without name is rejected. Parameters of the stage runner interface method are never
   * assigned the stage context, so the annotation would have no effect.
   * <p>
   * This method is invoked from the constructor. It must not be made overridable, as an override would run before the
   * subclass has been initialized.
   *
   * @param dataAnnotation  optional {@link Data @Data} annotation on the parameter, may be {@code null}
   * @param parameterNames  reflected parameter names of the stage runner interface method, may be {@code null}
   * @param p               index of the parameter to resolve
   *
   * @return  the resolved data name, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if the parameter is annotated with {@code @Data} without name or no name
   *                                            can be derived for the parameter
   */
  @Contract(pure = true)
  private @NotNull String getDataNameForParameter(Data dataAnnotation, String[] parameterNames, int p)
  {
    if (dataAnnotation != null)
    {
      var dataName = dataAnnotation.name();
      if (dataName.isEmpty())
      {
        throw new StageRunnerConfigurationException("@Data without name is not allowed for parameter " + (p + 1) +
            " in stage runner function " + stageRunnerInterfaceMethod);
      }

      return dataName;
    }

    var parameterName = parameterNames != null ? parameterNames[p] : null;
    if (!hasLength(parameterName))
    {
      throw new StageRunnerConfigurationException("unable to detect data name for parameter " + (p + 1) +
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
   * This happens once, after all non-lazy singletons have been instantiated, so the result does not depend on the order
   * in which beans are created:
   * <ul>
   *   <li>
   *     Singletons which already exist are analyzed using their instance.
   *   </li>
   *   <li>
   *     Singletons which have not been created yet, like lazy-init singletons or objects created by factory beans, are
   *     analyzed by their declared type: the return type of a {@code @Bean} method, the bean class or the object type
   *     of a factory bean. They are only created if the declared type has stage functions. Stage functions declared
   *     only by the actual class of the bean, for example a subclass of an interface typed {@code @Bean} method, are
   *     therefore not found. Enable {@link #setCreateLazySingletons(boolean) setCreateLazySingletons} to create these
   *     singletons regardless of their declared type.
   *   </li>
   *   <li>
   *     The target bean of a scoped proxy ({@code scopedTarget.*}) is skipped. Its stage functions are discovered and
   *     invoked through the scoped proxy, which is the bean exposed to the application. This works for singleton and
   *     non-singleton scopes alike.
   *   </li>
   * </ul>
   * Non singleton beans are skipped. Until this method has completed, running the stage runner fails with a
   * {@link StageRunnerException}.
   *
   * @throws StageRunnerConfigurationException  if a stage function of a bean cannot be registered
   * @throws IllegalStateException              if the owning bean factory is not a
   *                                            {@link ConfigurableListableBeanFactory}
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
      // the target of a scoped proxy is analyzed through its scoped proxy
      if (isScopedTarget(beanName))
        continue;

      try {
        if (listableBeanFactory.containsSingleton(beanName) && !listableBeanFactory.isFactoryBean(beanName))
          analyseStageFunctions(listableBeanFactory.getBean(beanName));
        else
        {
          var beanType = listableBeanFactory.getType(beanName, false);
          if (beanType != null && ScopedObject.class.isAssignableFrom(beanType))
          {
            var targetType = determineTargetClass(listableBeanFactory, getTargetBeanName(beanName));
            if (targetType != null)
              beanType = targetType;
          }

          if (createLazySingletons || beanType != null && hasStageFunctions(ClassUtils.getUserClass(beanType)))
            analyseStageFunctions(listableBeanFactory.getBean(beanName));
          else if (logger.isDebugEnabled())
          {
            logger.debug("skip bean '" + beanName + "' which has not been created yet: declared type " +
                (beanType == null ? "unknown" : beanType.getName()) + " has no stage functions");
          }
        }
      } catch(StageRunnerConfigurationException ex) {
        throw new StageRunnerConfigurationException(
            "failed to register stage functions of bean '" + beanName + "': " + ex.getMessage(), ex);
      }
    }

    stageFunctionsDiscovered = true;
  }


  /**
   * Scans the public methods of the given bean, including inherited ones, for methods carrying the configured stage
   * function annotation and registers each match. Non-public methods are not considered. Beans whose type cannot carry
   * the annotation according to {@link org.springframework.core.annotation.AnnotationUtils#isCandidateClass(Class,
   * Class) AnnotationUtils.isCandidateClass}, such as JDK types, are skipped without scanning their methods.
   * <p>
   * A static stage function method is registered only for the first analyzed bean whose class declares or inherits it.
   * For any other bean exposing the same method, the method is skipped.
   * <p>
   * Each bean instance is analyzed only once, so a bean instance which is exposed by more than one bean name has its
   * stage functions registered only once. Different proxies for the same target are different bean instances.
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
    final var beanType = determineStageFunctionType(bean);

    if (!isCandidateClass(beanType, annotationType) || !analysedBeans.add(bean))
      return;

    for(var method: beanType.getMethods())
      if (isStageFunction(method) && (!isStatic(method.getModifiers()) || analysedStaticStageFunctions.add(method)))
      {
        registerStageFunction(
            findMergedAnnotationAttributes(method, annotationType, false, false),
            method, bean);
      }
  }


  /**
   * Determines the class to scan for stage function methods of the given bean.
   * <p>
   * For a proxy whose target is obtained from the bean factory, like a scoped proxy, the class is determined from the
   * target bean definition. The target class reported by the proxy itself depends on whether the target bean had
   * already been created when the proxy was built: it is either the user class or, if the target is proxied itself, the
   * generated proxy class. For all other beans, the ultimate target class is used. In either case, a generated CGLIB
   * subclass is resolved to its user class.
   *
   * @param bean  bean to inspect, not {@code null}
   *
   * @return  class declaring the stage function methods of {@code bean}, never {@code null}
   *
   * @since 0.4.1
   */
  @Contract(pure = true)
  private @NotNull Class<?> determineStageFunctionType(@NotNull Object bean)
  {
    if (bean instanceof Advised advised &&
        advised.getTargetSource() instanceof AbstractBeanFactoryBasedTargetSource targetSource &&
        beanFactory instanceof ConfigurableListableBeanFactory listableBeanFactory)
    {
      var targetType = determineTargetClass(listableBeanFactory, targetSource.getTargetBeanName());
      if (targetType != null)
        return ClassUtils.getUserClass(targetType);
    }

    return ClassUtils.getUserClass(ultimateTargetClass(bean));
  }


  /**
   * Tells whether the given type declares at least one public stage function method, including inherited ones. Types
   * which cannot carry the annotation according to
   * {@link org.springframework.core.annotation.AnnotationUtils#isCandidateClass(Class, Class)
   * AnnotationUtils.isCandidateClass}, such as JDK types, are rejected without scanning their methods.
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
    if (!isCandidateClass(type, stageFunctionAnnotation.getAnnotationType()))
      return false;

    for(var method: type.getMethods())
      if (isStageFunction(method))
        return true;

    return false;
  }


  /**
   * Tells whether the given method is a stage function method, i.e. it carries the configured stage function
   * annotation.
   * <p>
   * The annotation is looked up with Spring's merged annotation semantics. It may be declared on the method itself, on
   * a method it overrides or implements in a superclass or interface, or through a composed annotation which is
   * meta-annotated with the stage function annotation.
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
    return
        !method.isBridge() &&
        !method.isSynthetic() &&
        hasAnnotation(method, stageFunctionAnnotation.getAnnotationType());
  }


  /**
   * Registers a single annotated method as a stage function on the internal stage runner factory. Registration is
   * skipped when the configured {@link StageFunctionFilter} rejects the function. If a non-empty name is provided by
   * the name generator the function is registered as a named function, otherwise it is added anonymously.
   *
   * @param stageFunctionAnnotationAttributes  merged attributes of the stage function annotation, not {@code null}
   * @param method                             the annotated method, not {@code null}
   * @param bean                               the bean the method belongs to, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the method cannot be adapted to a stage function or the function name
   *                                            is not unique
   */
  protected void registerStageFunction(@NotNull AnnotationAttributes stageFunctionAnnotationAttributes,
                                       @NotNull Method method,
                                       @NotNull Object bean)
  {
    final var stageEnum = stageFunctionAnnotation.getStage(stageFunctionAnnotationAttributes);
    final var order = stageFunctionAnnotation.getOrder(stageFunctionAnnotationAttributes);
    final var generatedName = stageFunctionNameGenerator.apply(stageFunctionAnnotationAttributes, method);
    final var name = hasLength(generatedName) ? generatedName : null;

    if (stageFunctionFilter.filter(bean, stageEnum, order, name))
    {
      final var description = stageFunctionAnnotation.getDescription(stageFunctionAnnotationAttributes);
      final var function = getStageFunctionBuilder()
          .createStageFunction(stageFunctionAnnotation, dataNameTypeMap, method, bean);

      if (logger.isDebugEnabled())
      {
        final var msg = new StringBuilder();

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

      addStageFunction(stageRunnerFactory, name, stageEnum, order, description, function);
    }
  }


  /**
   * Adds a regular stage function or, if {@code name} is not {@code null}, registers a named stage function with the
   * given factory.
   *
   * @param factory      stage runner factory, not {@code null}
   * @param name         stage function name or {@code null} for a regular stage function
   * @param stage        stage, which must be a constant of the factory's stage type, not {@code null}
   * @param order        order within the stage
   * @param description  stage function description or {@code null}
   * @param function     stage function for the factory's stage type, not {@code null}
   *
   * @param <S>  stage enumeration type
   *
   * @throws StageRunnerConfigurationException  if {@code name} is not unique for the factory
   *
   * @since 0.4.0
   */
  @SuppressWarnings("unchecked")
  private static <S extends Enum<S>> void addStageFunction(@NotNull DefaultStageRunnerFactory<S> factory, String name,
                                                           @NotNull Enum<?> stage, int order, String description,
                                                           @NotNull StageFunction<?> function)
  {
    if (name != null)
      factory.namedStageFunction(name, (S)stage, order, description, (StageFunction<S>)function);
    else
      factory.addStageFunction((S)stage, order, description, (StageFunction<S>)function);
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
    final var bean = new RootBeanDefinition(stageRunnerInterfaceType, SCOPE_SINGLETON, this::createStageRunnerProxy);

    bean.setTargetType(ResolvableType.forClass(stageRunnerInterfaceType));
    bean.setLenientConstructorResolution(false);
    bean.setLazyInit(true);
    bean.setDescription("Auto-detected StageRunner for " + stageFunctionAnnotation.getStageType().getName());
    bean.setPrimary(true);

    final var name = stageRunnerInterfaceType.getName();

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
        stageRunnerParameters,
        (StageRunnerFactory<S>)stageRunnerFactory);
  }


  /**
   * Locates the single abstract method of the given functional interface and validates its return type. The return type
   * must be either {@code boolean} or {@code void}.
   * <p>
   * Abstract methods inherited from super-interfaces are taken into account, whereas default, static, private and
   * synthetic methods as well as abstract redeclarations of public {@link Object} methods are ignored. An abstract
   * method declared by multiple super-interfaces with the same signature is counted once.
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
  static @NotNull Method findFunctionalInterfaceMethod(@NotNull Class<?> interfaceType)
  {
    if (!interfaceType.isInterface())
      throw new StageRunnerConfigurationException(interfaceType.getName() + " is not an interface");

    Method functionalInterfaceMethod = null;

    for(var method: interfaceType.getMethods())
      if (isAbstract(method.getModifiers()) && !isObjectMethod(method))
      {
        if (functionalInterfaceMethod != null && !hasSameSignature(functionalInterfaceMethod, method))
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
   * Tells whether both methods have the same name, parameter types and return type.
   *
   * @param method1  first method, not {@code null}
   * @param method2  second method, not {@code null}
   *
   * @return  {@code true} if both methods have the same signature, {@code false} otherwise
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  private static boolean hasSameSignature(@NotNull Method method1, @NotNull Method method2)
  {
    return method1.getName().equals(method2.getName()) &&
           method1.getReturnType() == method2.getReturnType() &&
           Arrays.equals(method1.getParameterTypes(), method2.getParameterTypes());
  }


  /**
   * Returns the stage runner interface implemented by the registered proxy bean.
   *
   * @return  stage runner interface type, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull Class<R> getStageRunnerInterfaceType() {
    return stageRunnerInterfaceType;
  }


  /**
   * Returns the functional method of the stage runner interface.
   *
   * @return  stage runner interface method, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull Method getStageRunnerInterfaceMethod() {
    return stageRunnerInterfaceMethod;
  }


  /**
   * Returns the descriptor of the stage function annotation marking the bean methods to register.
   *
   * @return  stage function annotation descriptor, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull StageFunctionAnnotation getStageFunctionAnnotation() {
    return stageFunctionAnnotation;
  }


  /**
   * Returns the internal stage runner factory, which is populated with the discovered stage functions and used by the
   * stage runner proxy.
   * <p>
   * The stage type must be passed explicitly so that a mismatch with the stage type of the stage function annotation is
   * detected here, instead of failing later when the stage runner is executed.
   *
   * @param stageType  stage enumeration type, as returned by {@link StageFunctionAnnotation#getStageType()}, not
   *                   {@code null}
   *
   * @return  stage runner factory, never {@code null}
   *
   * @param <S>  stage enumeration type
   *
   * @throws IllegalArgumentException  if {@code stageType} is not the stage type of the stage function annotation
   * @throws NullPointerException      if {@code stageType} is {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  protected <S extends Enum<S>> @NotNull DefaultStageRunnerFactory<S> getStageRunnerFactory(@NotNull Class<S> stageType)
  {
    final var annotationStageType = stageFunctionAnnotation.getStageType();

    if (requireNonNull(stageType, "stageType must not be null") != annotationStageType)
    {
      throw new IllegalArgumentException("stage type " + stageType.getName() + " does not match stage type " +
          annotationStageType.getName() + " of the stage function annotation");
    }

    return (DefaultStageRunnerFactory<S>)stageRunnerFactory;
  }


  /**
   * Returns how each parameter of the stage runner interface method is passed to the stage runner, indexed by
   * parameter position.
   *
   * @return  unmodifiable list of parameter descriptions, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull @Unmodifiable List<StageRunnerParameter> getStageRunnerParameters() {
    return stageRunnerParameters;
  }


  /**
   * Returns the mapping of data names to the types of the corresponding stage runner interface method parameters.
   *
   * @return  unmodifiable data name to type mapping, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull @Unmodifiable Map<String,ResolvableType> getDataNameTypeMap() {
    return dataNameTypeMap;
  }


  /**
   * Returns the bean factory owning this processor.
   *
   * @return  bean factory, or {@code null} if it has not been set yet
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected BeanFactory getBeanFactory() {
    return beanFactory;
  }


  /**
   * Returns the stage runner proxy builder.
   *
   * @return  stage runner proxy builder, or {@code null} if neither a builder has been set nor
   *          {@link #afterPropertiesSet()} has been invoked yet
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected StageRunnerProxyBuilder getStageRunnerProxyBuilder() {
    return stageRunnerProxyBuilder;
  }


  /**
   * Returns the filter deciding which discovered stage functions are registered.
   *
   * @return  stage function filter, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull StageFunctionFilter getStageFunctionFilter() {
    return stageFunctionFilter;
  }


  /**
   * Returns the generator providing the stage function name for an annotated method.
   *
   * @return  stage function name generator, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull BiFunction<AnnotationAttributes,Method,String> getStageFunctionNameGenerator() {
    return stageFunctionNameGenerator;
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
   * <p>
   * The conversion service is only used by the default stage function builder, so it must be set before a stage
   * function builder has been set or the default builder has been created.
   *
   * @param conversionService  conversion service, not {@code null}
   *
   * @throws NullPointerException   if {@code conversionService} is {@code null}
   * @throws IllegalStateException  if the stage function builder already exists
   *
   * @since 0.3.1
   */
  @Contract(mutates = "this")
  public synchronized void setConversionService(ConversionService conversionService)
  {
    requireNonNull(conversionService, "conversionService must not be null");

    if (stageFunctionBuilder != null)
      throw new IllegalStateException("conversion service must be set before the stage function builder exists");

    this.conversionService = conversionService;
  }


  /**
   * Sets the builder used to create the stage runner proxy. When no builder is set explicitly, a default implementation
   * is used.
   *
   * @param stageRunnerProxyBuilder  stage runner proxy builder, not {@code null}
   *
   * @throws NullPointerException  if {@code stageRunnerProxyBuilder} is {@code null}
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
   * @throws NullPointerException  if {@code stageFunctionBuilder} is {@code null}
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
   * @throws NullPointerException  if {@code stageFunctionFilter} is {@code null}
   */
  @Contract(mutates = "this")
  public void setStageFunctionFilter(@NotNull StageFunctionFilter stageFunctionFilter) {
    this.stageFunctionFilter = requireNonNull(stageFunctionFilter, "stageFunctionFilter must not be null");
  }


  /**
   * Controls whether annotations declared on the stage runner interface method are copied to the generated proxy
   * method. The setting only applies to the default stage runner proxy builder and is disabled by default.
   * <p>
   * The setting must be changed before a stage runner proxy builder has been set or the default builder has been
   * created in {@link #afterPropertiesSet()}.
   *
   * @param copyInterfaceMethodAnnotations  {@code true} to copy interface method annotations, {@code false} otherwise
   *
   * @throws IllegalStateException  if the stage runner proxy builder already exists
   *
   * @since 0.3.1
   */
  @Contract(mutates = "this")
  public void setCopyInterfaceMethodAnnotations(boolean copyInterfaceMethodAnnotations)
  {
    if (stageRunnerProxyBuilder != null)
    {
      throw new IllegalStateException(
          "copy interface method annotations must be set before the stage runner proxy builder exists");
    }

    this.copyInterfaceMethodAnnotations = copyInterfaceMethodAnnotations;
  }


  /**
   * Controls whether singletons which have not been created yet are created during discovery, regardless of their
   * declared type. This affects lazy-init singletons and objects created by factory beans. The setting is disabled by
   * default.
   * <p>
   * If disabled, such a singleton is only created if its declared type (the return type of a {@code @Bean} method, the
   * bean class or the object type of a factory bean) has stage functions. If the stage functions are declared only by
   * the actual class of the bean, they are not discovered. Enabling this setting creates every such singleton, so its
   * actual class is analyzed, at the cost of no longer creating lazy-init singletons lazily. Alternatively, declare the
   * actual class as the return type of the {@code @Bean} method.
   *
   * @param createLazySingletons  {@code true} to create all singletons during discovery, {@code false} to create only
   *                              singletons whose declared type has stage functions
   *
   * @throws IllegalStateException  if the stage functions have already been discovered
   *
   * @since 0.4.1
   */
  @Contract(mutates = "this")
  public void setCreateLazySingletons(boolean createLazySingletons)
  {
    if (stageFunctionsDiscovered)
      throw new IllegalStateException("create lazy singletons must be set before the stage functions are discovered");

    this.createLazySingletons = createLazySingletons;
  }


  /**
   * Sets the generator which provides the stage function name for an annotated method.
   * <p>
   * By default, the stage function name is provided by the
   * &#x40;{@link de.sayayi.lib.stagerunner.spring.annotation.StageDefinition.Name Name} annotation. Using a stage
   * function name generator the name can be calculated dynamically from the annotation attributes and the method.
   * <p>
   * If the name generator returns {@code null} or an empty string, the stage function is added as a regular stage
   * function, which is executed for every stage runner execution. Otherwise, it is registered as a named stage
   * function.
   *
   * @param stageFunctionNameGenerator  stage function name generator, not {@code null}
   *
   * @throws NullPointerException  if {@code stageFunctionNameGenerator} is {@code null}
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
