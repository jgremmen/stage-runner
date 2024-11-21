package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.*;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import de.sayayi.lib.stagerunner.spring.builder.AbstractStageFunctionBuilder;
import de.sayayi.lib.stagerunner.spring.builder.ByteBuddyHelper.AbstractImplementation;
import de.sayayi.lib.stagerunner.spring.builder.StageFunctionBuilder;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy.SuffixingRandom;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.bytecode.*;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveBoxingDelegate;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
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
import java.util.*;
import java.util.stream.Stream;

import static de.sayayi.lib.stagerunner.spring.builder.ByteBuddyHelper.parameterizedType;
import static de.sayayi.lib.stagerunner.spring.builder.ByteBuddyHelper.typeDescription;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static net.bytebuddy.description.modifier.Visibility.PRIVATE;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS;
import static net.bytebuddy.matcher.ElementMatchers.*;
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
  private final boolean copyInterfaceMethodAnnotations;
  private final Method stageRunnerInterfaceMethod;
  private final String[] dataNames;
  private final Map<String,ResolvableType> dataNameTypeMap;

  private BeanFactory beanFactory;
  private ConversionService conversionService;
  private AbstractStageFunctionBuilder stageFunctionBuilder;


  public StageRunnerFactoryProcessor(@NotNull Class<R> stageRunnerInterfaceType,
                                     @NotNull Class<? extends Annotation> stageFunctionAnnotation) {
    this(stageRunnerInterfaceType, stageFunctionAnnotation, false);
  }


  @SuppressWarnings("unchecked")
  public StageRunnerFactoryProcessor(@NotNull Class<R> stageRunnerInterfaceType,
                                     @NotNull Class<? extends Annotation> stageFunctionAnnotation,
                                     boolean copyInterfaceMethodAnnotations)
  {
    this.stageRunnerInterfaceType = stageRunnerInterfaceType;
    this.stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotation);
    this.stageRunnerFactory = new DefaultStageRunnerFactory(this.stageFunctionAnnotation.getStageType());
    this.copyInterfaceMethodAnnotations = copyInterfaceMethodAnnotations;

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
    if (conversionService == null)
    {
      try {
        conversionService = beanFactory.getBean(ConversionService.class);
      } catch(NullPointerException | NoSuchBeanDefinitionException ex) {
        conversionService = DefaultConversionService.getSharedInstance();
      }
    }

    analyseStageRunnerInterfaceMethod();

    stageFunctionBuilder = new StageFunctionBuilder(
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
  private @NotNull R createStageRunnerProxy()
  {
    var stageType = stageFunctionAnnotation.getStageType();
    var stageRunnerFactoryType = parameterizedType(StageRunnerFactory.class, stageType);
    var method = new MethodDescription.ForLoadedMethod(stageRunnerInterfaceMethod);

    try {
      //noinspection resource
      return new ByteBuddy()
          .with(new SuffixingRandom(stageType.getSimpleName()))
          .subclass(stageRunnerInterfaceType, NO_CONSTRUCTORS)
          .modifiers(PUBLIC)
          .defineField("factory", stageRunnerFactoryType, PRIVATE, FieldManifestation.FINAL)
          .defineConstructor(PUBLIC)
              .withParameters(stageRunnerFactoryType)
              .intercept(new ProxyConstructorImplementation())
          .define(stageRunnerInterfaceMethod)
              .intercept(new ProxyMethodImplementation(method))
              .annotateMethod(copyInterfaceMethodAnnotations ? method.getDeclaredAnnotations() : List.of())
          .method(isToString())
              .intercept(FixedValue.value("Proxy implementation for interface " + stageRunnerInterfaceType.getName()))
          .make()
          .load(stageRunnerInterfaceType.getClassLoader())
          .getLoaded()
          .getDeclaredConstructor(StageRunnerFactory.class)
          .newInstance(stageRunnerFactory);
    } catch(Exception ex) {
      throw new StageRunnerConfigurationException("failed to create proxy for stage runner interface " +
          stageRunnerInterfaceType, ex);
    }
  }


  @Contract(pure = true)
  @NotNull Method findFunctionalInterfaceMethod(@NotNull Class<?> interfaceType)
  {
    if (!interfaceType.isInterface())
      throw new StageRunnerConfigurationException(interfaceType.getName() + " is not an interface");

    Method functionalInterfaceMethod = null;

    for(var method: interfaceType.getMethods())
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




  private static final class ProxyConstructorImplementation extends AbstractImplementation
  {
    @Override
    public @NotNull ByteCodeAppender appender(@NotNull Target target)
    {
      return new ByteCodeAppender.Simple(
          // super();
          MethodVariableAccess.loadThis(),
          MethodInvocation.invoke(typeDescription(Object.class)
              .getDeclaredMethods()
              .filter(isConstructor())
              .getOnly()),
          // this.factory = factory(param 1)
          MethodVariableAccess.loadThis(),
          MethodVariableAccess.REFERENCE.loadFrom(1),
          FieldAccess
              .forField(target
                  .getInstrumentedType()
                  .getDeclaredFields()
                  .filter(named("factory"))
                  .getOnly())
              .write(),
          // return
          MethodReturn.VOID);
    }
  }




  private final class ProxyMethodImplementation extends AbstractImplementation
  {
    private final MethodDescription method;


    private ProxyMethodImplementation(@NotNull MethodDescription method) {
      this.method = method;
    }


    @Override
    public @NotNull ByteCodeAppender appender(@NotNull Target target)
    {
      var stackManipulations = new ArrayList<StackManipulation>();

      // this.factory.createRunner() -> stack
      stackManipulations.add(MethodVariableAccess.loadThis());
      stackManipulations.add(FieldAccess
          .forField(target
              .getInstrumentedType()
              .getDeclaredFields()
              .filter(named("factory"))
              .getOnly())
          .read());
      stackManipulations.add(MethodInvocation.invoke(
          typeDescription(StageRunnerFactory.class)
              .getDeclaredMethods()
              .filter(named("createRunner"))
              .getOnly()));

      // param1 = data map
      stackManipulations.addAll(Stream.of(dataNames).anyMatch(Objects::nonNull)
          ? buildMapWithDataNames()
          : buildMapNoDataNames());

      // param2 = callback (optional)
      var stageRunnerCallbackParameter = findCallbackParameter();
      if (stageRunnerCallbackParameter != null)
        stackManipulations.add(MethodVariableAccess.load(stageRunnerCallbackParameter));

      stackManipulations.add(MethodInvocation.invoke(
          typeDescription(StageRunner.class)
              .getDeclaredMethods()
              .filter(named("run").and(takesArguments(stageRunnerCallbackParameter != null ? 2 : 1)))
              .getOnly()));

      if (method.getReturnType().represents(void.class))
      {
        stackManipulations.add(Removal.SINGLE);
        stackManipulations.add(MethodReturn.VOID);
      }
      else
        stackManipulations.add(MethodReturn.INTEGER);

      return new ByteCodeAppender.Simple(stackManipulations);
    }


    @Contract(pure = true)
    private @NotNull List<StackManipulation> buildMapWithDataNames()
    {
      var stackManipulations = new ArrayList<StackManipulation>();
      var hashMapType = typeDescription(HashMap.class);

      // new HashMap() -> stack
      stackManipulations.add(TypeCreation.of(hashMapType));
      stackManipulations.add(Duplication.SINGLE);
      stackManipulations.add(MethodInvocation.invoke(hashMapType
          .getDeclaredMethods()
          .filter(isDefaultConstructor())
          .getOnly()));

      var mapPutMethod = typeDescription(Map.class)
          .getDeclaredMethods()
          .filter(named("put").and(takesArguments(2)))
          .getOnly();
      String dataName;

      for(var parameter: method.getParameters())
        if ((dataName = dataNames[parameter.getIndex()]) != null)
        {
          stackManipulations.add(Duplication.SINGLE);
          stackManipulations.add(new TextConstant(dataName));
          stackManipulations.add(MethodVariableAccess.load(parameter));

          var parameterType = parameter.getType().asErasure();
          if (parameterType.isPrimitive())
          {
            stackManipulations.add(PrimitiveBoxingDelegate
                .forPrimitive(parameterType)
                .assignBoxedTo(
                    TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Object.class),
                    Assigner.DEFAULT, Assigner.Typing.STATIC));
          }

          stackManipulations.add(MethodInvocation.invoke(mapPutMethod));
          stackManipulations.add(Removal.SINGLE);
        }

      stackManipulations.add(MethodInvocation.invoke(
          typeDescription(Map.class)
              .getDeclaredMethods()
              .filter(named("copyOf"))
              .getOnly()));

      return stackManipulations;
    }


    @Contract(pure = true)
    private @NotNull List<StackManipulation> buildMapNoDataNames()
    {
      return List.of(MethodInvocation.invoke(
          typeDescription(Map.class)
              .getDeclaredMethods()
              .filter(named("of").and(takesNoArguments()))
              .getOnly()));
    }


    @Contract(pure = true)
    private ParameterDescription findCallbackParameter()
    {
      var stageRunnerCallbackType = typeDescription(StageRunnerCallback.class);

      for(var parameter: method.getParameters())
        if (stageRunnerCallbackType.isAssignableFrom(parameter.getType().asErasure()))
          return parameter;

      return null;
    }
  }
}
