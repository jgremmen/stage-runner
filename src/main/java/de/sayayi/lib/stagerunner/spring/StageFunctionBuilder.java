package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spring.ByteBuddyHelper.AbstractImplementation;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.NamingStrategy.Suffixing.BaseNameResolver;
import net.bytebuddy.NamingStrategy.SuffixingRandom;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.Duplication;
import net.bytebuddy.implementation.bytecode.Removal;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveUnboxingDelegate;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.sayayi.lib.stagerunner.spring.AbstractStageFunctionBuilder.TypeQualifier.CONVERTABLE;
import static de.sayayi.lib.stagerunner.spring.ByteBuddyHelper.parameterizedType;
import static de.sayayi.lib.stagerunner.spring.ByteBuddyHelper.typeDescription;
import static net.bytebuddy.description.modifier.TypeManifestation.FINAL;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.named;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
class StageFunctionBuilder extends AbstractStageFunctionBuilder
{
  private static final FieldAccess.Defined FIELD_ACCESS_BEAN = FieldAccess
      .forField(typeDescription(AbstractStageFunction.class)
          .getDeclaredFields()
          .filter(named("bean"))
          .getOnly());

  private static final MethodDescription METHOD_CONTEXT_GET_DATA =
      typeDescription(StageContext.class)
          .getDeclaredMethods()
          .filter(named("getData"))
          .getOnly();

  private static final MethodDescription METHOD_STAGE_FUNCTION_CONVERT =
      typeDescription(AbstractStageFunctionWithConversion.class)
          .getDeclaredMethods()
          .filter(named("convert"))
          .getOnly();

  private static final MethodDescription METHOD_STAGE_FUNCTION_CHECK_NOT_NULL =
      typeDescription(AbstractStageFunction.class)
          .getDeclaredMethods()
          .filter(named("checkNotNull"))
          .getOnly();

  private static final BaseNameResolver BASE_NAME_RESOLVER =
      new BaseNameResolver.ForGivenType(typeDescription(StageFunction.class));

  private final Map<CacheKey,Class<? extends StageFunction<?>>> stageFunctionClassCache;


  StageFunctionBuilder(@NotNull Class<? extends Annotation> stageFunctionAnnotationType,
                       @NotNull ConversionService conversionService,
                       @NotNull Map<String,ResolvableType> dataTypeMap)
  {
    super(stageFunctionAnnotationType, conversionService, dataTypeMap);

    stageFunctionClassCache = new ConcurrentHashMap<>();
  }


  @Override
  protected @NotNull <S extends Enum<S>> StageFunction<S> buildFor(
      Object bean, @NotNull Method method, @NotNull NameWithQualifierAndType[] parameters)
      throws ReflectiveOperationException
  {
    final MethodDescription methodDescription = new MethodDescription.ForLoadedMethod(method);

    if (methodDescription.isStatic())
      bean = null;

    return Arrays.stream(parameters).anyMatch(NameWithQualifierAndType::isConvertableQualifier)
        ? buildForWithConversion(bean, methodDescription, parameters)
        : buildForNoConversion(bean, methodDescription, parameters);
  }


  protected @NotNull <S extends Enum<S>> StageFunction<S> buildForNoConversion(
      Object bean, @NotNull MethodDescription method, @NotNull NameWithQualifierAndType[] parameters)
      throws ReflectiveOperationException
  {
    final Class<? extends StageFunction<S>> stageFunctionClass = createStageFunctionType(
        parameterizedType(AbstractStageFunction.class, stageFunctionAnnotation.getStageType()),
        method, parameters);

    return stageFunctionClass
        .getDeclaredConstructor(Object.class)
        .newInstance(bean);
  }


  protected @NotNull <S extends Enum<S>> StageFunction<S> buildForWithConversion(
      Object bean,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters) throws ReflectiveOperationException
  {
    final Class<? extends StageFunction<S>> stageFunctionClass = createStageFunctionType(
        parameterizedType(AbstractStageFunctionWithConversion.class, stageFunctionAnnotation.getStageType()),
        method, parameters);

    return stageFunctionClass
        .getDeclaredConstructor(Object.class, ConversionService.class, TypeDescriptor[].class)
        .newInstance(bean, conversionService, Arrays
            .stream(parameters)
            .map(p -> p.isConvertableQualifier() ? p.getType() : null)
            .toArray(TypeDescriptor[]::new));
  }


  @SuppressWarnings({"unchecked", "resource"})
  protected @NotNull <S extends Enum<S>> Class<? extends StageFunction<S>> createStageFunctionType(
      @NotNull TypeDescription.Generic superType,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters)
  {
    return (Class<? extends StageFunction<S>>)stageFunctionClassCache
        .computeIfAbsent(new CacheKey(method, parameters), ck -> (Class<? extends StageFunction<?>>)
            new ByteBuddy()
                .with(createNamingStrategy(method))
                .subclass(superType)
                .modifiers(PUBLIC, FINAL)
                .method(named("process"))
                    .intercept(new ProcessMethodImplementation(method, parameters))
                .method(isToString())
                    .intercept(implToString(method))
                .make()
                .load(stageFunctionAnnotation.getAnnotationType().getClassLoader())
                .getLoaded());
  }


  @Contract(pure = true)
  protected @NotNull NamingStrategy createNamingStrategy(@NotNull MethodDescription method)
  {
    return new SuffixingRandom(
        stageFunctionAnnotation.getStageType().getSimpleName() + '$' + method.getName(),
        BASE_NAME_RESOLVER);
  }


  @Contract(pure = true)
  protected @NotNull Implementation implToString(@NotNull MethodDescription method) {
    return FixedValue.value(StageFunction.class.getSimpleName() + " adapter for " + method);
  }




  public static abstract class AbstractStageFunction<S extends Enum<S>> implements StageFunction<S>
  {
    protected final Object bean;


    protected AbstractStageFunction(Object bean) {
      this.bean = bean;
    }


    @Contract("null, _ -> fail")
    @SuppressWarnings("unused")
    protected void checkNotNull(Object value, @NotNull String dataName)
    {
      if (value == null)
        throw new StageRunnerException("Data value '" + dataName + "' must not be null");
    }
  }




  public static abstract class AbstractStageFunctionWithConversion<S extends Enum<S>> extends AbstractStageFunction<S>
  {
    private final @NotNull ConversionService conversionService;
    private final @NotNull TypeDescriptor[] targetTypes;


    protected AbstractStageFunctionWithConversion(Object bean,
                                                  @NotNull ConversionService conversionService,
                                                  @NotNull TypeDescriptor[] targetTypes)
    {
      super(bean);

      this.conversionService = conversionService;
      this.targetTypes = targetTypes;
    }


    @SuppressWarnings("unused")
    protected Object convert(Object value, int p) {
      return conversionService.convert(value, TypeDescriptor.forObject(value), targetTypes[p]);
    }
  }




  private static final class CacheKey
  {
    private final @NotNull MethodDescription method;
    private final @NotNull NameWithQualifierAndType[] parameters;


    private CacheKey(@NotNull MethodDescription method, @NotNull NameWithQualifierAndType[] parameters)
    {
      this.method = method;
      this.parameters = parameters;
    }


    @Override
    @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass", "EqualsDoesntCheckParameterClass"})
    public boolean equals(Object o)
    {
      if (this == o)
        return true;

      final CacheKey that = (CacheKey)o;

      return method.equals(that.method) && Arrays.equals(parameters, that.parameters);
    }


    @Override
    public int hashCode() {
      return method.hashCode() * 31 + Arrays.hashCode(parameters);
    }
  }




  private static final class ProcessMethodImplementation extends AbstractImplementation
  {
    private final MethodDescription method;
    private final NameWithQualifier[] parameters;


    private ProcessMethodImplementation(@NotNull MethodDescription method, @NotNull NameWithQualifier[] parameters)
    {
      this.method = method;
      this.parameters = parameters;
    }


    @Override
    public @NotNull ByteCodeAppender appender(@NotNull Target target)
    {
      final List<StackManipulation> stackManipulations = new ArrayList<>();
      final ParameterList<?> methodParameters = method.getParameters();

      if (!method.isStatic())
      {
        stackManipulations.add(MethodVariableAccess.loadThis());
        stackManipulations.add(FIELD_ACCESS_BEAN.read());
        stackManipulations.add(TypeCasting.to(method.getDeclaringType()));
      }

      for(int p = 0; p < parameters.length; p++)
      {
        final NameWithQualifier parameter = parameters[p];
        final String dataName = parameter.getName();

        if ("$context".equals(dataName))
          stackManipulations.add(MethodVariableAccess.REFERENCE.loadFrom(1));
        else
        {
          if (parameter.getQualifier() != CONVERTABLE)
          {
            // context.getData(dataName)
            stackManipulations.add(MethodVariableAccess.REFERENCE.loadFrom(1));
            stackManipulations.add(new TextConstant(dataName));
            stackManipulations.add(MethodInvocation.invoke(METHOD_CONTEXT_GET_DATA));
          }
          else
          {
            // this.convert(context.getData(dataName), p)
            stackManipulations.add(MethodVariableAccess.loadThis());
            stackManipulations.add(MethodVariableAccess.REFERENCE.loadFrom(1));
            stackManipulations.add(new TextConstant(dataName));
            stackManipulations.add(MethodInvocation.invoke(METHOD_CONTEXT_GET_DATA));
            stackManipulations.add(IntegerConstant.forValue(p));
            stackManipulations.add(MethodInvocation.invoke(METHOD_STAGE_FUNCTION_CONVERT));
          }

          stackManipulations.addAll(castToParameterType(methodParameters.get(p).getType(), dataName));
        }
      }

      stackManipulations.add(MethodInvocation.invoke(method));
      stackManipulations.add(Removal.of(method.getReturnType()));
      stackManipulations.add(MethodReturn.VOID);

      return new ByteCodeAppender.Simple(stackManipulations.toArray(StackManipulation[]::new));
    }


    @Contract(pure = true)
    private @NotNull List<StackManipulation> castToParameterType(@NotNull TypeDescription.Generic methodParameterType,
                                                                 @NotNull String dataName)
    {
      final List<StackManipulation> stackManipulations = new ArrayList<>();

      if (methodParameterType.isPrimitive())
      {
        // checkNotNull(<value>, dataName)
        stackManipulations.add(Duplication.SINGLE);
        stackManipulations.add(MethodVariableAccess.loadThis());
        stackManipulations.add(ByteBuddyHelper.SWAP);
        stackManipulations.add(new TextConstant(dataName));
        stackManipulations.add(MethodInvocation.invoke(METHOD_STAGE_FUNCTION_CHECK_NOT_NULL));

        // cast -> primitive
        stackManipulations.add(TypeCasting.to(methodParameterType.asErasure().asBoxed()));
        stackManipulations.add(PrimitiveUnboxingDelegate.forPrimitive(methodParameterType));
      }
      else
        stackManipulations.add(TypeCasting.to(methodParameterType));

      return stackManipulations;
    }
  }
}
