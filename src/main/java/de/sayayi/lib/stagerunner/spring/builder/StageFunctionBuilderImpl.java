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
package de.sayayi.lib.stagerunner.spring.builder;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import de.sayayi.lib.stagerunner.spring.StageFunctionAnnotation;
import de.sayayi.lib.stagerunner.spring.StageFunctionBuilder;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.MethodManifestation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
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
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.utility.RandomString;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.bytebuddy.description.modifier.TypeManifestation.FINAL;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class StageFunctionBuilderImpl extends AbstractBuilder implements StageFunctionBuilder
{
  private final ConversionService conversionService;
  private final Map<CacheKey,Class<? extends StageFunction<?>>> stageFunctionClassCache;
  private final RandomString randomString;


  public StageFunctionBuilderImpl(@NotNull ConversionService conversionService)
  {
    this.conversionService = conversionService;

    stageFunctionClassCache = new ConcurrentHashMap<>();
    randomString = new RandomString(5);
  }


  @Override
  public @NotNull <S extends Enum<S>> StageFunction<S> createStageFunction(
      @NotNull StageFunctionAnnotation stageFunctionAnnotation,
      @NotNull Map<String,ResolvableType> dataNameTypeMap,
      @NotNull Method stageFunction,
      @NotNull Object bean)
  {
    var methodParameters = stageFunction.getParameters();
    var parameters = new NameWithQualifierAndType[methodParameters.length];
    var stageContextType = forClassWithGenerics(
        StageContext.class, stageFunctionAnnotation.getStageType());

    for(int p = 0; p < methodParameters.length; p++)
    {
      var parameterType = new TypeDescriptor(new MethodParameter(stageFunction, p));

      parameters[p] = new NameWithQualifierAndType(
          parameterType.getResolvableType().isAssignableFrom(stageContextType)
              ? new NameWithQualifier("$context", TypeQualifier.ASSIGNABLE)
              : findNameWithQualifier(methodParameters[p], parameterType, dataNameTypeMap),
          parameterType);
    }

    var methodDescription = new MethodDescription.ForLoadedMethod(stageFunction);
    if (methodDescription.isStatic())
      bean = null;

    try {
      return Arrays.stream(parameters).anyMatch(NameWithQualifierAndType::isConvertableQualifier)
          ? buildForWithConversion(bean, methodDescription, parameters, stageFunctionAnnotation)
          : buildForNoConversion(bean, methodDescription, parameters, stageFunctionAnnotation);
    } catch(ReflectiveOperationException ex) {
      throw new StageRunnerConfigurationException(
          "failed to generate stage function for method " + methodDescription, ex);
    }
  }


  private @NotNull <S extends Enum<S>> StageFunction<S> buildForNoConversion(
      Object bean, @NotNull MethodDescription method, @NotNull NameWithQualifierAndType[] parameters,
      @NotNull StageFunctionAnnotation stageFunctionAnnotation) throws ReflectiveOperationException
  {
    final Class<? extends StageFunction<S>> stageFunctionClass = createStageFunctionType(
        parameterizedType(AbstractStageFunction.class, stageFunctionAnnotation.getStageType()),
        method, parameters, stageFunctionAnnotation);

    return stageFunctionClass
        .getDeclaredConstructor(Object.class)
        .newInstance(bean);
  }


  private @NotNull <S extends Enum<S>> StageFunction<S> buildForWithConversion(
      Object bean,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters,
      @NotNull StageFunctionAnnotation stageFunctionAnnotation) throws ReflectiveOperationException
  {
    final Class<? extends StageFunction<S>> stageFunctionClass = createStageFunctionType(
        parameterizedType(AbstractStageFunctionWithConversion.class, stageFunctionAnnotation.getStageType()),
        method, parameters, stageFunctionAnnotation);

    return stageFunctionClass
        .getDeclaredConstructor(Object.class, ConversionService.class, TypeDescriptor[].class)
        .newInstance(bean, conversionService, Arrays
            .stream(parameters)
            .map(p -> p.isConvertableQualifier() ? p.type : null)
            .toArray(TypeDescriptor[]::new));
  }


  @SuppressWarnings("unchecked")
  private @NotNull <S extends Enum<S>> Class<? extends StageFunction<S>> createStageFunctionType(
      @NotNull TypeDescription.Generic superType,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters,
      @NotNull StageFunctionAnnotation stageFunctionAnnotation)
  {
    return (Class<? extends StageFunction<S>>)stageFunctionClassCache
        .computeIfAbsent(
            new CacheKey(method, parameters),
            ck -> buildStageFunctionClass(superType, method, parameters, stageFunctionAnnotation));
  }


  @SuppressWarnings({"unchecked", "resource"})
  private @NotNull Class<? extends StageFunction<?>> buildStageFunctionClass(
      @NotNull TypeDescription.Generic superType,
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters,
      @NotNull StageFunctionAnnotation stageFunctionAnnotation)
  {
    var className = StageFunction.class.getName() +
        '$' + stageFunctionAnnotation.getStageType().getSimpleName() +
        '$' + method.getName() +
        '$' + randomString.nextString();

    return (Class<? extends StageFunction<?>>)
        new ByteBuddy()
            .subclass(superType)
            .name(className)
            .modifiers(PUBLIC, FINAL)
            .defineMethod("process", void.class, PUBLIC, MethodManifestation.FINAL)
                .withParameter(typeDescription(StageContext.class), "stageContext")
                .intercept(new ProcessMethodImplementation(method, parameters))
            .method(isToString())
                .intercept(FixedValue.value(StageFunction.class.getSimpleName() + " adapter for " + method))
            .make()
            .load(stageFunctionAnnotation.getAnnotationType().getClassLoader(), INJECTION)
            .getLoaded();
  }


  @Contract(pure = true)
  private @NotNull NameWithQualifier findNameWithQualifier(@NotNull Parameter parameter,
                                                           @NotNull TypeDescriptor parameterType,
                                                           @NotNull Map<String,ResolvableType> dataNameTypeMap)
  {
    var nameWithQualifier =
        findNameWithQualifierByParameterName(parameter, parameterType, dataNameTypeMap);

    if (nameWithQualifier == null &&
        (nameWithQualifier = findNameWithQualifierByParameterType(parameter, parameterType, dataNameTypeMap)) == null)
    {
      throw new StageRunnerConfigurationException("Unknown data type for parameter " + parameter +
          "; please specify @Data annotation and/or extend the conversion service");
    }

    return nameWithQualifier;
  }


  @Contract(pure = true)
  private NameWithQualifier findNameWithQualifierByParameterName(@NotNull Parameter parameter,
                                                                 @NotNull TypeDescriptor parameterType,
                                                                 @NotNull Map<String,ResolvableType> dataNameTypeMap)
  {
    ResolvableType dataType;

    var dataAnnotation = findMergedAnnotation(parameter, Data.class);
    if (dataAnnotation != null)
    {
      var dataName = dataAnnotation.name();
      if (!hasLength(dataName))
        throw new StageRunnerConfigurationException("@Data name must not be empty for parameter " + parameter);

      if ((dataType = dataNameTypeMap.get(dataName)) != null)
        return new NameWithQualifier(dataName, qualifyParameterTypeOrFail(parameterType, dataType));

      throw new StageRunnerConfigurationException("Unknown @Data name '" + dataName + "' for parameter " + parameter);
    }

    var parameterName = parameter.getName();
    if (hasLength(parameterName) && (dataType = dataNameTypeMap.get(parameterName)) != null)
      return new NameWithQualifier(parameterName, qualifyParameterTypeOrFail(parameterType, dataType));

    return null;
  }


  @Contract(pure = true)
  private NameWithQualifier findNameWithQualifierByParameterType(@NotNull Parameter parameter,
                                                                 @NotNull TypeDescriptor parameterType,
                                                                 @NotNull Map<String,ResolvableType> dataNameTypeMap)
  {
    var nameQualifiers = new ArrayList<NameWithQualifier>();

    dataNameTypeMap.forEach((name, type) -> {
      var q = qualifyParameterType(parameterType, type);
      if (q != null)
      {
        var nwq = new NameWithQualifier(name, q);
        if (!nameQualifiers.contains(nwq))
          nameQualifiers.add(nwq);
      }
    });

    NameWithQualifier nameWithQualifier = null;

    if (!nameQualifiers.isEmpty())
    {
      nameWithQualifier = nameQualifiers.get(0);
      if (nameQualifiers.size() > 1)
      {
        nameQualifiers.sort(null);

        var nwq2 = nameQualifiers.get(1);

        if (nameWithQualifier.qualifier == nwq2.qualifier && !nameWithQualifier.name.equals(nwq2.name))
        {
          throw new StageRunnerConfigurationException("Ambiguous type for parameter " + parameter +
              "; please specify @Data annotation");
        }
      }
    }

    return nameWithQualifier;
  }


  @Contract(pure = true)
  private TypeQualifier qualifyParameterType(@NotNull TypeDescriptor parameterType,
                                             @NotNull ResolvableType dataType)
  {
    var parameterResolvableType = parameterType.getResolvableType();

    if (parameterResolvableType.getType().equals(dataType.getType()))
      return TypeQualifier.IDENTICAL;

    if (parameterResolvableType.getRawClass() == Object.class)
      return TypeQualifier.ANYTHING;

    if (parameterResolvableType.isAssignableFrom(dataType))
      return TypeQualifier.ASSIGNABLE;

    if (conversionService.canConvert(new TypeDescriptor(dataType, null, null), parameterType))
      return TypeQualifier.CONVERTABLE;

    return null;
  }


  @Contract(pure = true)
  private @NotNull TypeQualifier qualifyParameterTypeOrFail(@NotNull TypeDescriptor parameterType,
                                                            @NotNull ResolvableType dataType)
  {
    var qualifier = qualifyParameterType(parameterType, dataType);
    if (qualifier == null)
      throw new IllegalStateException("Unsupported parameter type: " + parameterType);

    return qualifier;
  }




  private static class NameWithQualifierAndType extends NameWithQualifier
  {
    final @NotNull TypeDescriptor type;


    private NameWithQualifierAndType(@NotNull NameWithQualifier nameWithQualifier, @NotNull TypeDescriptor type)
    {
      super(nameWithQualifier.name, nameWithQualifier.qualifier);

      this.type = type;
    }


    @Contract(pure = true)
    public boolean isConvertableQualifier() {
      return qualifier == TypeQualifier.CONVERTABLE;
    }


    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;
      if (!(o instanceof NameWithQualifierAndType))
        return false;

      var that = (NameWithQualifierAndType)o;

      return
          qualifier == that.qualifier &&
          name.equals(that.name) &&
          type.getResolvableType().equals(that.type.getResolvableType());
    }


    @Override
    public int hashCode() {
      return super.hashCode() * 31 + type.hashCode();
    }


    @Override
    public String toString() {
      return "NameWithQualifierAndType(name=" + name + ",qualifier=" + qualifier + ",type=" + type + ')';
    }
  }




  private static class NameWithQualifier implements Comparable<NameWithQualifier>
  {
    final @NotNull String name;
    final @NotNull TypeQualifier qualifier;


    private NameWithQualifier(@NotNull String name, @NotNull TypeQualifier qualifier)
    {
      this.name = name;
      this.qualifier = qualifier;
    }


    @Override
    public int compareTo(@NotNull NameWithQualifier o)
    {
      int cmp = qualifier.compareTo(o.qualifier);
      return cmp == 0 ? name.compareTo(o.name) : cmp;
    }


    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;
      if (!(o instanceof NameWithQualifier))
        return false;

      var that = (NameWithQualifier)o;

      return qualifier == that.qualifier && name.equals(that.name);
    }


    @Override
    public int hashCode() {
      return name.hashCode() * 31 + qualifier.hashCode();
    }


    @Override
    public String toString() {
      return "NameWithQualifier(name=" + name + ",qualifier=" + qualifier + ')';
    }
  }




  public enum TypeQualifier
  {
    IDENTICAL,
    ASSIGNABLE,
    CONVERTABLE,
    ANYTHING
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

      var that = (CacheKey)o;

      return method.equals(that.method) && Arrays.equals(parameters, that.parameters);
    }


    @Override
    public int hashCode() {
      return method.hashCode() * 31 + Arrays.hashCode(parameters);
    }
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




  private static final class ProcessMethodImplementation extends AbstractImplementation
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

    private static final @NotNull StackManipulation SWAP = new StackManipulation.AbstractBase() {
      @Override
      public @NotNull Size apply(@NotNull MethodVisitor methodVisitor, @NotNull Context context)
      {
        methodVisitor.visitInsn(Opcodes.SWAP);
        return Size.ZERO;
      }
    };

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
      var stackManipulations = new ArrayList<StackManipulation>();
      var methodParameters = method.getParameters();

      if (!method.isStatic())
      {
        stackManipulations.add(MethodVariableAccess.loadThis());
        stackManipulations.add(FIELD_ACCESS_BEAN.read());
        stackManipulations.add(TypeCasting.to(method.getDeclaringType()));
      }

      for(int p = 0; p < parameters.length; p++)
      {
        var parameter = parameters[p];
        var dataName = parameter.name;

        if ("$context".equals(dataName))
          stackManipulations.add(MethodVariableAccess.REFERENCE.loadFrom(1));
        else
        {
          if (parameter.qualifier != TypeQualifier.CONVERTABLE)
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
      var stackManipulations = new ArrayList<StackManipulation>();

      if (methodParameterType.isPrimitive())
      {
        // checkNotNull(<value>, dataName)
        stackManipulations.add(Duplication.SINGLE);
        stackManipulations.add(MethodVariableAccess.loadThis());
        stackManipulations.add(SWAP);
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
