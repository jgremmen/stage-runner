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
import de.sayayi.lib.stagerunner.spring.StageFunctionAnnotation;
import de.sayayi.lib.stagerunner.spring.StageFunctionBuilder;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.MethodManifestation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.Duplication;
import net.bytebuddy.implementation.bytecode.Removal;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.TypeCasting;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveUnboxingDelegate;
import net.bytebuddy.implementation.bytecode.collection.ArrayAccess;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.bytebuddy.description.modifier.TypeManifestation.FINAL;
import static net.bytebuddy.description.modifier.Visibility.PRIVATE;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS;
import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.springframework.core.BridgeMethodResolver.findBridgedMethod;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;
import static org.springframework.util.StringUtils.hasLength;


/**
 * Default {@link StageFunctionBuilder} implementation that adapts a Spring managed bean method annotated with a stage
 * function annotation into an executable {@link StageFunction}.
 * <p>
 * For every unique combination of target method and parameter binding a dedicated {@link StageFunction} class is
 * generated at runtime using ByteBuddy and cached. Method parameters receive the stage context or an entry of the stage
 * runner data map, matched by name or by type, as described by the {@link Data @Data} annotation. Parameters that
 * require type conversion are converted using a configurable {@link ConversionService}.
 * <p>
 * The generated classes implement {@link StageFunction} directly and only refer to types of exported packages, Spring
 * and the JDK. This way they can be defined in the package of the bean class, even if the bean class is part of a named
 * application module.
 * <p>
 * Instances are thread safe; generated classes are cached per builder instance.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@ApiStatus.Internal
public final class StageFunctionBuilderImpl extends AbstractBuilder implements StageFunctionBuilder
{
  /**
   * Name of the generated field holding the target bean. The field value is {@code null} for static methods.
   *
   * @since 0.4.0
   */
  private static final String BEAN_FIELD_NAME = "bean";

  /**
   * Name of the generated field holding the conversion service.
   *
   * @since 0.4.0
   */
  private static final String CONVERSION_SERVICE_FIELD_NAME = "conversionService";

  /**
   * Name of the generated field holding the target type for each method parameter, with {@code null} for parameters
   * which do not require a conversion.
   *
   * @since 0.4.0
   */
  private static final String TARGET_TYPES_FIELD_NAME = "targetTypes";


  /** Conversion service used for parameters requiring a conversion. */
  private final ConversionService conversionService;

  /** Cache of generated stage function classes. */
  private final Map<CacheKey,Class<? extends StageFunction<?>>> stageFunctionClassCache;


  /**
   * Creates a new stage function builder.
   *
   * @param conversionService  conversion service used to convert data map values into method parameter types, not
   *                           {@code null}
   */
  public StageFunctionBuilderImpl(@NotNull ConversionService conversionService)
  {
    this.conversionService = conversionService;

    stageFunctionClassCache = new ConcurrentHashMap<>();
  }


  /**
   * {@inheritDoc}
   * <p>
   * A parameter of type {@link StageContext}, which is not annotated with {@link Data @Data}, receives the stage
   * context itself. A {@code @Data} annotation without name is only allowed on a parameter which can be assigned the
   * stage context; it prevents the stage context from being bound and the parameter is resolved as if it was not
   * annotated. If the method is static, {@code bean} is ignored.
   * <p>
   * Parameters are bound using {@code stageFunction} as declared on the target class. If {@code bean} is a JDK dynamic
   * proxy, the generated stage function invokes the corresponding interface method on the proxy instead.
   *
   * @throws StageRunnerConfigurationException  if {@code bean} is a JDK dynamic proxy which does not expose
   *                                            {@code stageFunction} through one of its interfaces, or if a parameter
   *                                            which cannot be assigned the stage context is annotated with
   *                                            {@code @Data} without name, or if a parameter cannot be bound to the
   *                                            stage context or a data entry
   */
  @Override
  public @NotNull <S extends Enum<S>> StageFunction<S> createStageFunction(
      @NotNull StageFunctionAnnotation stageFunctionAnnotation,
      @NotNull Map<String,ResolvableType> dataNameTypeMap,
      @NotNull Method stageFunction,
      @NotNull Object bean)
  {
    final var methodParameters = stageFunction.getParameters();
    final var parameters = new NameWithQualifierAndType[methodParameters.length];
    final var stageContextType = forClassWithGenerics(
        StageContext.class, stageFunctionAnnotation.getStageType());

    for(var p = 0; p < methodParameters.length; p++)
    {
      final var parameterType = new TypeDescriptor(new MethodParameter(stageFunction, p));
      final var dataAnnotation = findMergedAnnotation(methodParameters[p], Data.class);
      final var acceptsStageContext = parameterType.getResolvableType().isAssignableFrom(stageContextType);

      // @Data without a name only serves to prevent the stage context from being bound to the parameter
      if (dataAnnotation != null && !hasLength(dataAnnotation.name()) && !acceptsStageContext)
      {
        throw new StageRunnerConfigurationException(
            "@Data without name is only allowed for a parameter which can be assigned the stage context: parameter " +
            methodParameters[p] + " of method " + stageFunction);
      }

      final var isStageContext =
          dataAnnotation == null && parameterType.getType() == StageContext.class && acceptsStageContext;

      parameters[p] = new NameWithQualifierAndType(
          isStageContext
              ? new NameWithQualifier("$context", TypeQualifier.ASSIGNABLE)
              : findNameWithQualifier(methodParameters[p], parameterType, dataNameTypeMap),
          parameterType);
    }

    var invocableMethod = stageFunction;
    if (Modifier.isStatic(stageFunction.getModifiers()))
      bean = null;
    else
      invocableMethod = selectInvocableMethod(stageFunction, bean);

    final var methodDescription = new MethodDescription.ForLoadedMethod(invocableMethod);
    final var hostLookup = hostLookup(invocableMethod.getDeclaringClass());

    try {
      return this.<S>createStageFunctionType(methodDescription, parameters, stageFunctionAnnotation, hostLookup)
          .getDeclaredConstructor(Object.class, ConversionService.class, TypeDescriptor[].class)
          .newInstance(bean, conversionService, Arrays
              .stream(parameters)
              .map(p -> p.isConvertableQualifier() ? p.type : null)
              .toArray(TypeDescriptor[]::new));
    } catch(ReflectiveOperationException ex) {
      throw new StageRunnerConfigurationException(
          "failed to generate stage function for method " + methodDescription, ex);
    }
  }


  /**
   * Returns the method to invoke on {@code bean} for the given stage function method.
   * <p>
   * If {@code bean} is a JDK dynamic proxy, the method declared on the target class cannot be invoked on it. In that
   * case the corresponding method of an interface implemented by the proxy is returned. If {@code stageFunction}
   * implements a generic interface method, the interface declares the erased signature; that interface method is found
   * through the bridge method of {@code stageFunction}, which forwards the invocation to {@code stageFunction}.
   * <p>
   * For all other beans, including class based (CGLIB) proxies, {@code stageFunction} is returned unchanged.
   *
   * @param stageFunction  stage function method, as declared on the target class, not {@code null}
   * @param bean           bean instance, possibly a proxy, not {@code null}
   *
   * @return  method which can be invoked on {@code bean}, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if {@code bean} is a proxy which does not expose {@code stageFunction}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  private static @NotNull Method selectInvocableMethod(@NotNull Method stageFunction, @NotNull Object bean)
  {
    final var beanType = bean.getClass();

    try {
      return AopUtils.selectInvocableMethod(stageFunction, beanType);
    } catch(IllegalStateException ex) {
      for(var method: stageFunction.getDeclaringClass().getDeclaredMethods())
        if (method.isBridge() && findBridgedMethod(method).equals(stageFunction))
        {
          try {
            return AopUtils.selectInvocableMethod(method, beanType);
          } catch(IllegalStateException ignored) {
            // bridge method is not exposed by the proxy either
          }
        }

      throw new StageRunnerConfigurationException("stage function " + stageFunction +
          " cannot be invoked on proxy " + beanType.getName() + "; declare the method in an interface " +
          "implemented by the proxy or use class based proxies", ex);
    }
  }


  /**
   * Returns the generated stage function class for the given method and parameter binding, either from the cache or by
   * building a fresh class through {@link #buildStageFunctionClass}.
   *
   * @param method                   target method, not {@code null}
   * @param parameters               resolved parameter bindings, not {@code null}
   * @param stageFunctionAnnotation  stage function annotation descriptor, not {@code null}
   * @param hostLookup               lookup on the class declaring {@code method}, not {@code null}
   *
   * @return  generated stage function class, never {@code null}
   *
   * @param <S>  stage enumeration type
   */
  @SuppressWarnings("unchecked")
  private @NotNull <S extends Enum<S>> Class<? extends StageFunction<S>> createStageFunctionType(
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters,
      @NotNull StageFunctionAnnotation stageFunctionAnnotation,
      @NotNull MethodHandles.Lookup hostLookup)
  {
    return (Class<? extends StageFunction<S>>)stageFunctionClassCache
        .computeIfAbsent(
            new CacheKey(method, stageFunctionAnnotation.getStageType(), List.of(parameters)),
            ck -> buildStageFunctionClass(method, parameters, stageFunctionAnnotation, hostLookup));
  }


  /**
   * Generates a new {@link StageFunction} class that invokes {@code method} on its bean with the values obtained from
   * the stage context data map.
   * <p>
   * The class is defined through {@code hostLookup} in the package and class loader of the class declaring
   * {@code method}. This way the generated class can access the declaring class and its parameter types, even if they
   * are not public. The generated class does not extend a class of this library, as the library's internal packages
   * are not accessible from an application module.
   * <p>
   * The generated class has a public constructor taking the bean (or {@code null} for a static method), the conversion
   * service and the target type for each method parameter, with {@code null} for parameters which do not require a
   * conversion.
   *
   * @param method                   target method, not {@code null}
   * @param parameters               resolved parameter bindings, not {@code null}
   * @param stageFunctionAnnotation  stage function annotation descriptor, not {@code null}
   * @param hostLookup               lookup on the class declaring {@code method}, not {@code null}
   *
   * @return  generated stage function class, never {@code null}
   */
  @SuppressWarnings({"unchecked", "resource"})
  private @NotNull Class<? extends StageFunction<?>> buildStageFunctionClass(
      @NotNull MethodDescription method,
      @NotNull NameWithQualifierAndType[] parameters,
      @NotNull StageFunctionAnnotation stageFunctionAnnotation,
      @NotNull MethodHandles.Lookup hostLookup)
  {
    final var hostClass = hostLookup.lookupClass();
    var className = hostClass.getName() +
        "$StageFunction" +
        '$' + stageFunctionAnnotation.getStageType().getSimpleName() +
        '$' + method.getName() +
        '$' + randomString.nextString();

    return (Class<? extends StageFunction<?>>)
        new ByteBuddy()
            .subclass(Object.class, NO_CONSTRUCTORS)
            .implement(parameterizedType(StageFunction.class, stageFunctionAnnotation.getStageType()))
            .name(className)
            .modifiers(PUBLIC, FINAL)
            .defineField(BEAN_FIELD_NAME, Object.class, PRIVATE, FieldManifestation.FINAL)
            .defineField(CONVERSION_SERVICE_FIELD_NAME, ConversionService.class, PRIVATE, FieldManifestation.FINAL)
            .defineField(TARGET_TYPES_FIELD_NAME, TypeDescriptor[].class, PRIVATE, FieldManifestation.FINAL)
            .defineConstructor(PUBLIC)
                .withParameters(Object.class, ConversionService.class, TypeDescriptor[].class)
                .intercept(MethodCall
                    .invoke(typeDescription(Object.class).getDeclaredMethods().filter(isConstructor()).getOnly())
                    .andThen(FieldAccessor.ofField(BEAN_FIELD_NAME).setsArgumentAt(0))
                    .andThen(FieldAccessor.ofField(CONVERSION_SERVICE_FIELD_NAME).setsArgumentAt(1))
                    .andThen(FieldAccessor.ofField(TARGET_TYPES_FIELD_NAME).setsArgumentAt(2)))
            .defineMethod("process", void.class, PUBLIC, MethodManifestation.FINAL)
                .withParameter(typeDescription(StageContext.class), "stageContext")
                .intercept(new ProcessMethodImplementation(method, parameters))
            .method(isToString())
                .intercept(FixedValue.value(StageFunction.class.getSimpleName() + " adapter for " + method))
            .make()
            .load(hostClass.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(hostLookup))
            .getLoaded();
  }


  /**
   * Resolves the data name and type qualifier for {@code parameter}. A parameter annotated with a named
   * {@link Data @Data} is resolved by that name only. Otherwise, resolution is attempted by the parameter name and
   * falls back to matching by type if no entry with that name exists.
   *
   * @param parameter        method parameter, not {@code null}
   * @param parameterType    type descriptor of the method parameter, not {@code null}
   * @param dataNameTypeMap  mapping of data names to their types, not {@code null}
   *
   * @return  the resolved data name and qualifier, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if no matching data map entry can be determined or the match is
   *                                            ambiguous
   */
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


  /**
   * Resolves the data name and type qualifier for {@code parameter} by matching a {@link Data @Data} annotation or the
   * parameter name against {@code dataNameTypeMap}.
   *
   * @param parameter        method parameter, not {@code null}
   * @param parameterType    type descriptor of the method parameter, not {@code null}
   * @param dataNameTypeMap  mapping of data names to their types, not {@code null}
   *
   * @return  the resolved data name and qualifier, or {@code null} if the parameter is not annotated with a named
   *          {@code @Data} and no data entry matches the parameter name
   *
   * @throws StageRunnerConfigurationException  if the {@code @Data} name is unknown, or if the named data entry has a
   *                                            type which cannot be supplied to the parameter
   */
  @Contract(pure = true)
  private NameWithQualifier findNameWithQualifierByParameterName(@NotNull Parameter parameter,
                                                                 @NotNull TypeDescriptor parameterType,
                                                                 @NotNull Map<String,ResolvableType> dataNameTypeMap)
  {
    ResolvableType dataType;

    var dataAnnotation = findMergedAnnotation(parameter, Data.class);
    if (dataAnnotation != null && hasLength(dataAnnotation.name()))
    {
      var dataName = dataAnnotation.name();

      if ((dataType = dataNameTypeMap.get(dataName)) != null)
      {
        return new NameWithQualifier(dataName,
            qualifyParameterTypeOrFail(parameter, parameterType, dataName, dataType));
      }

      throw new StageRunnerConfigurationException("Unknown @Data name '" + dataName + "' for parameter " + parameter);
    }

    var parameterName = parameter.getName();
    if (hasLength(parameterName) && (dataType = dataNameTypeMap.get(parameterName)) != null)
    {
      return new NameWithQualifier(parameterName,
          qualifyParameterTypeOrFail(parameter, parameterType, parameterName, dataType));
    }

    return null;
  }


  /**
   * Resolves the data name and type qualifier for {@code parameter} by matching all data map entries against the
   * parameter type. The strongest match is selected; when multiple equally strong matches exist the resolution is
   * considered ambiguous.
   *
   * @param parameter        method parameter, not {@code null}
   * @param parameterType    type descriptor of the method parameter, not {@code null}
   * @param dataNameTypeMap  mapping of data names to their types, not {@code null}
   *
   * @return  the best matching data name and qualifier, or {@code null} if no data entry matches the parameter type
   *
   * @throws StageRunnerConfigurationException  if more than one data map entry qualifies with the same qualifier
   */
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
      nameQualifiers.sort(null);
      nameWithQualifier = nameQualifiers.get(0);

      if (nameQualifiers.size() > 1)
      {
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


  /**
   * Determines how a data map value of type {@code dataType} can be supplied for a method parameter of
   * {@code parameterType}.
   *
   * @param parameterType  type descriptor of the method parameter, not {@code null}
   * @param dataType       type of the data map value, not {@code null}
   *
   * @return  the {@link TypeQualifier} describing the match strength, or {@code null} when there is no match
   */
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


  /**
   * Variant of {@link #qualifyParameterType} that throws a {@link StageRunnerConfigurationException} instead of
   * returning {@code null} when the parameter type cannot be supplied from a value of {@code dataType}.
   *
   * @param parameter      method parameter, not {@code null}
   * @param parameterType  type descriptor of the method parameter, not {@code null}
   * @param dataName       name of the data map entry, not {@code null}
   * @param dataType       type of the data map value, not {@code null}
   *
   * @return  the {@link TypeQualifier} describing the match strength, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if there is no match
   */
  @Contract(pure = true)
  private @NotNull TypeQualifier qualifyParameterTypeOrFail(@NotNull Parameter parameter,
                                                            @NotNull TypeDescriptor parameterType,
                                                            @NotNull String dataName,
                                                            @NotNull ResolvableType dataType)
  {
    var qualifier = qualifyParameterType(parameterType, dataType);
    if (qualifier == null)
    {
      throw new StageRunnerConfigurationException("Data '" + dataName + "' of type " + dataType +
          " cannot be supplied to parameter " + parameter + " of method " + parameter.getDeclaringExecutable());
    }

    return qualifier;
  }




  /**
   * A {@link NameWithQualifier} enriched with the resolved Spring {@link TypeDescriptor} of the associated method
   * parameter. The type descriptor is used when a conversion is required at invocation time.
   */
  private static class NameWithQualifierAndType extends NameWithQualifier
  {
    /** Type descriptor of the method parameter. */
    final @NotNull TypeDescriptor type;


    /**
     * Creates a new parameter binding.
     *
     * @param nameWithQualifier  data name and qualifier, not {@code null}
     * @param type               type descriptor of the method parameter, not {@code null}
     */
    private NameWithQualifierAndType(@NotNull NameWithQualifier nameWithQualifier, @NotNull TypeDescriptor type)
    {
      super(nameWithQualifier.name, nameWithQualifier.qualifier);

      this.type = type;
    }


    /**
     * Indicates whether the value bound to this parameter needs to be converted through the {@link ConversionService}
     * before it can be passed to the target method.
     *
     * @return  {@code true} if a conversion is required, {@code false} otherwise
     */
    @Contract(pure = true)
    public boolean isConvertableQualifier() {
      return qualifier == TypeQualifier.CONVERTABLE;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o)
    {
      return
          super.equals(o) &&
          type.getResolvableType().equals(((NameWithQualifierAndType)o).type.getResolvableType());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
      return super.hashCode() * 31 + type.hashCode();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return "NameWithQualifierAndType(name=" + name + ",qualifier=" + qualifier + ",type=" + type + ')';
    }
  }




  /**
   * Pairing of a data map key ({@code name}) with the {@link TypeQualifier} describing how the associated data value
   * can be supplied to a method parameter. Instances have a natural order that favors stronger qualifiers so that the
   * best match can be picked when several candidates exist.
   */
  private static class NameWithQualifier implements Comparable<NameWithQualifier>
  {
    /** Data map key, or {@code $context} for the stage context itself. */
    final @NotNull String name;

    /** Match strength of the data value for the method parameter. */
    final @NotNull TypeQualifier qualifier;


    /**
     * Creates a new data name and qualifier pair.
     *
     * @param name       data map key, not {@code null}
     * @param qualifier  match strength, not {@code null}
     */
    private NameWithQualifier(@NotNull String name, @NotNull TypeQualifier qualifier)
    {
      this.name = name;
      this.qualifier = qualifier;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(@NotNull NameWithQualifier o)
    {
      final var cmp = qualifier.compareTo(o.qualifier);
      return cmp == 0 ? name.compareTo(o.name) : cmp;
    }


    /**
     * {@inheritDoc}
     * <p>
     * Only instances of the exact same class are equal, which keeps this relation symmetric with respect to the
     * {@link NameWithQualifierAndType} subclass.
     */
    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;

      if (o == null || o.getClass() != getClass())
        return false;

      final var that = (NameWithQualifier)o;

      return qualifier == that.qualifier && name.equals(that.name);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
      return name.hashCode() * 31 + qualifier.hashCode();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return "NameWithQualifier(name=" + name + ",qualifier=" + qualifier + ')';
    }
  }




  /**
   * Describes how a data map value fits a method parameter. The declaration order also reflects the match strength: an
   * earlier constant is a stronger match than a later one.
   */
  public enum TypeQualifier
  {
    /** The parameter type and the data value type are identical. */
    IDENTICAL,

    /** The data value type is assignable to the parameter type without conversion. */
    ASSIGNABLE,

    /** The data value can be converted into the parameter type through the {@link ConversionService}. */
    CONVERTABLE,

    /** The parameter is declared as {@link Object} and therefore accepts any data value. */
    ANYTHING
  }




  /**
   * Cache key that identifies a generated stage function class by the target method, the stage type and its parameter
   * binding.
   * <p>
   * The parameter bindings are kept as a list, so they are compared by content rather than by reference.
   *
   * @param method      the target method, not {@code null}
   * @param stageType   the stage enumeration type implemented by the generated class, not {@code null}
   * @param parameters  the resolved parameter bindings, not {@code null}
   *
   * @since 0.4.0
   */
  private record CacheKey(@NotNull MethodDescription method,
                          @NotNull Class<?> stageType,
                          @NotNull List<NameWithQualifierAndType> parameters) {
  }




  /**
   * ByteBuddy {@link Implementation} for the generated {@code process(StageContext)} method. It loads each parameter
   * value from the stage context data map (or the context itself for the special {@code $context} binding), applies a
   * conversion where needed and finally invokes the target method on the stored bean.
   * <p>
   * The generated code only refers to the generated class itself, exported types of this library, Spring and the JDK.
   */
  private static final class ProcessMethodImplementation extends AbstractImplementation
  {
    /** Method description of {@link StageContext#getData(String)}. */
    private static final MethodDescription METHOD_CONTEXT_GET_DATA =
        typeDescription(StageContext.class)
            .getDeclaredMethods()
            .filter(named("getData"))
            .getOnly();

    /**
     * Method description of {@link ConversionService#convert(Object, TypeDescriptor, TypeDescriptor)}.
     *
     * @since 0.4.0
     */
    private static final MethodDescription METHOD_CONVERSION_SERVICE_CONVERT =
        typeDescription(ConversionService.class)
            .getDeclaredMethods()
            .filter(named("convert").and(takesArguments(Object.class, TypeDescriptor.class, TypeDescriptor.class)))
            .getOnly();

    /**
     * Method description of {@link TypeDescriptor#forObject(Object)}.
     *
     * @since 0.4.0
     */
    private static final MethodDescription METHOD_TYPE_DESCRIPTOR_FOR_OBJECT =
        typeDescription(TypeDescriptor.class)
            .getDeclaredMethods()
            .filter(named("forObject"))
            .getOnly();

    /**
     * Method description of {@link Objects#requireNonNull(Object, String)}.
     *
     * @since 0.4.0
     */
    private static final MethodDescription METHOD_OBJECTS_REQUIRE_NON_NULL =
        typeDescription(Objects.class)
            .getDeclaredMethods()
            .filter(named("requireNonNull").and(takesArguments(Object.class, String.class)))
            .getOnly();

    /** Target method. */
    private final MethodDescription method;

    /** Resolved parameter bindings. */
    private final NameWithQualifier[] parameters;


    /**
     * Creates a new implementation for the given target method.
     *
     * @param method      target method, not {@code null}
     * @param parameters  resolved parameter bindings, not {@code null}
     */
    private ProcessMethodImplementation(@NotNull MethodDescription method, @NotNull NameWithQualifier[] parameters)
    {
      this.method = method;
      this.parameters = parameters;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull ByteCodeAppender appender(@NotNull Target target)
    {
      final var stackManipulations = new ArrayList<StackManipulation>();
      final var methodParameters = method.getParameters();

      if (!method.isStatic())
      {
        // (DeclaringType)this.bean
        stackManipulations.add(MethodVariableAccess.loadThis());
        stackManipulations.add(field(target, BEAN_FIELD_NAME).read());
        stackManipulations.add(TypeCasting.to(method.getDeclaringType()));
      }

      for(int p = 0; p < parameters.length; p++)
      {
        final var parameter = parameters[p];
        final var dataName = parameter.name;

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
            // value = context.getData(dataName)
            // this.conversionService.convert(value, TypeDescriptor.forObject(value), this.targetTypes[p])
            stackManipulations.add(MethodVariableAccess.loadThis());
            stackManipulations.add(field(target, CONVERSION_SERVICE_FIELD_NAME).read());
            stackManipulations.add(MethodVariableAccess.REFERENCE.loadFrom(1));
            stackManipulations.add(new TextConstant(dataName));
            stackManipulations.add(MethodInvocation.invoke(METHOD_CONTEXT_GET_DATA));
            stackManipulations.add(Duplication.SINGLE);
            stackManipulations.add(MethodInvocation.invoke(METHOD_TYPE_DESCRIPTOR_FOR_OBJECT));
            stackManipulations.add(MethodVariableAccess.loadThis());
            stackManipulations.add(field(target, TARGET_TYPES_FIELD_NAME).read());
            stackManipulations.add(IntegerConstant.forValue(p));
            stackManipulations.add(ArrayAccess.REFERENCE.load());
            stackManipulations.add(MethodInvocation.invoke(METHOD_CONVERSION_SERVICE_CONVERT));
          }

          stackManipulations.addAll(castToParameterType(methodParameters.get(p).getType(), dataName));
        }
      }

      stackManipulations.add(MethodInvocation.invoke(method));
      stackManipulations.add(Removal.of(method.getReturnType()));
      stackManipulations.add(MethodReturn.VOID);

      return new ByteCodeAppender.Simple(stackManipulations.toArray(StackManipulation[]::new));
    }


    /**
     * Returns the field access for the field with the given {@code name}, declared by the generated class.
     *
     * @param target  implementation target, not {@code null}
     * @param name    field name, not {@code null}
     *
     * @return  field access, never {@code null}
     *
     * @since 0.4.0
     */
    @Contract(pure = true)
    private static @NotNull FieldAccess.Defined field(@NotNull Target target, @NotNull String name)
    {
      return FieldAccess.forField(target
          .getInstrumentedType()
          .getDeclaredFields()
          .filter(named(name))
          .getOnly());
    }


    /**
     * Returns the stack manipulations required to load a value onto the operand stack that matches
     * {@code methodParameterType}. For primitive parameters the value is checked for {@code null} and unboxed.
     *
     * @param methodParameterType  type of the method parameter, not {@code null}
     * @param dataName             data map key, used for the error message, not {@code null}
     *
     * @return  the stack manipulations for casting the value, never {@code null}
     */
    @Contract(pure = true)
    private @NotNull List<StackManipulation> castToParameterType(@NotNull TypeDescription.Generic methodParameterType,
                                                                 @NotNull String dataName)
    {
      if (!methodParameterType.isPrimitive())
        return List.of(TypeCasting.to(methodParameterType));

      return List.of(
          // Objects.requireNonNull(<value>, message)
          new TextConstant("Data value '" + dataName + "' must not be null"),
          MethodInvocation.invoke(METHOD_OBJECTS_REQUIRE_NON_NULL),

          // cast -> primitive
          TypeCasting.to(methodParameterType.asErasure().asBoxed()),
          PrimitiveUnboxingDelegate.forPrimitive(methodParameterType));
    }
  }
}
