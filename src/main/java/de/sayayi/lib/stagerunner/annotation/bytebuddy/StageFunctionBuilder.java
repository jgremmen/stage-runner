package de.sayayi.lib.stagerunner.annotation.bytebuddy;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.annotation.AbstractStageFunctionBuilder;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.NamingStrategy.Suffixing.BaseNameResolver;
import net.bytebuddy.NamingStrategy.SuffixingRandom;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
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

import static de.sayayi.lib.stagerunner.annotation.AbstractStageFunctionBuilder.TypeQualifier.CONVERTABLE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_FINAL;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PUBLIC;
import static net.bytebuddy.matcher.ElementMatchers.*;


public class StageFunctionBuilder<S extends Enum<S>> extends AbstractStageFunctionBuilder<S>
{
  private static final FieldAccess.Defined FIELD_ACCESS_BEAN = FieldAccess
      .forField(TypeDescription.ForLoadedType.of(AbstractStageFunction.class)
          .getDeclaredFields()
          .filter(named("bean"))
          .getOnly());

  private static final MethodDescription METHOD_CONTEXT_GET_DATA =
      TypeDescription.ForLoadedType.of(StageContext.class)
          .getDeclaredMethods()
          .filter(named("getData"))
          .getOnly();

  private static final MethodDescription METHOD_STAGE_FUNCTION_CONVERT =
      TypeDescription.ForLoadedType.of(AbstractStageFunctionWithConversion.class)
          .getDeclaredMethods()
          .filter(named("convert").and(takesArguments(2)))
          .getOnly();

  private final NamingStrategy namingStrategy;


  public StageFunctionBuilder(@NotNull Class<? extends Annotation> stageFunctionAnnotationType,
                              @NotNull ConversionService conversionService,
                              @NotNull Map<String,ResolvableType> dataTypeMap)
  {
    super(stageFunctionAnnotationType, conversionService, dataTypeMap);

    namingStrategy = new SuffixingRandom(stageFunctionAnnotation.getStageType().getSimpleName(),
        new BaseNameResolver.ForGivenType(TypeDescription.ForLoadedType.of(StageFunction.class)));
  }


  @Override
  protected @NotNull StageFunction<S> buildFor(@NotNull Object bean, @NotNull Method method,
                                               @NotNull NameWithQualifierAndType[] parameters)
      throws ReflectiveOperationException
  {
    final MethodDescription methodDescription = new MethodDescription.ForLoadedMethod(method);

    return Arrays.stream(parameters).anyMatch(p -> p.getQualifier() == CONVERTABLE)
        ? buildForWithConversion(bean, methodDescription, parameters)
        : buildForNoConversion(bean, methodDescription, parameters);
  }


  protected @NotNull StageFunction<S> buildForNoConversion(
      @NotNull Object bean,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters)
      throws ReflectiveOperationException
  {
    final Class<? extends StageFunction<S>> stageFunctionClass = createStageFunctionType(
        TypeDescription.Generic.Builder
            .parameterizedType(AbstractStageFunction.class, stageFunctionAnnotation.getStageType())
            .build(),
        method, parameters);

    return stageFunctionClass.getConstructor(Object.class).newInstance(bean);
  }


  protected @NotNull StageFunction<S> buildForWithConversion(
      @NotNull Object bean,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters) throws ReflectiveOperationException
  {
    final Class<? extends StageFunction<S>> stageFunctionClass = createStageFunctionType(
        TypeDescription.Generic.Builder
            .parameterizedType(AbstractStageFunctionWithConversion.class, stageFunctionAnnotation.getStageType())
            .build(),
        method, parameters);

    return stageFunctionClass
        .getConstructor(Object.class, ConversionService.class, TypeDescriptor[].class)
        .newInstance(bean, conversionService, Arrays
            .stream(parameters)
            .map(p -> p.getQualifier() == CONVERTABLE ? p.getType() : null)
            .toArray(TypeDescriptor[]::new));
  }


  @SuppressWarnings({"unchecked", "resource"})
  protected @NotNull Class<? extends StageFunction<S>> createStageFunctionType(
      @NotNull TypeDescription.Generic superType,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters)
  {
    return (Class<? extends StageFunction<S>>)new ByteBuddy()
        .with(namingStrategy)
        .subclass(superType)
        .modifiers(ACC_PUBLIC | ACC_FINAL)
        .method(named("process")).intercept(implProcess(method, parameters))
        .method(isToString()).intercept(implToString(method))
        .make()
        .load(stageFunctionAnnotation.getAnnotationType().getClassLoader())
        .getLoaded();
  }


  protected @NotNull Implementation implProcess(@NotNull MethodDescription method,
                                                @NotNull NameWithQualifier[] parameters)
  {
    final List<StackManipulation> stackManipulations = new ArrayList<>();
    final ParameterList<?> methodParameters = method.getParameters();

    stackManipulations.add(MethodVariableAccess.loadThis());
    stackManipulations.add(FIELD_ACCESS_BEAN.read());
    stackManipulations.add(TypeCasting.to(method.getDeclaringType()));

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

        final TypeDescription.Generic methodParameterType = methodParameters.get(p).getType();

        if (methodParameterType.isPrimitive())
        {
          stackManipulations.add(TypeCasting.to(methodParameterType.asErasure().asBoxed()));
          stackManipulations.add(PrimitiveUnboxingDelegate.forPrimitive(methodParameterType));
        }
        else
          stackManipulations.add(TypeCasting.to(methodParameters.get(p).getType()));
      }
    }

    stackManipulations.add(MethodInvocation.invoke(method));
    stackManipulations.add(Removal.of(method.getReturnType()));
    stackManipulations.add(MethodReturn.VOID);

    return new Implementation.Simple(stackManipulations.toArray(new StackManipulation[0]));
  }


  @Contract(pure = true)
  protected @NotNull Implementation implToString(@NotNull MethodDescription method) {
    return FixedValue.value(StageFunction.class.getSimpleName() + " proxy for " + method);
  }




  public static abstract class AbstractStageFunction<S extends Enum<S>> implements StageFunction<S>
  {
    protected final @NotNull Object bean;


    protected AbstractStageFunction(@NotNull Object bean) {
      this.bean = bean;
    }
  }




  public static abstract class AbstractStageFunctionWithConversion<S extends Enum<S>>
      extends AbstractStageFunction<S>
  {
    private final @NotNull ConversionService conversionService;
    private final @NotNull TypeDescriptor[] targetTypes;


    protected AbstractStageFunctionWithConversion(@NotNull Object bean,
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
}
