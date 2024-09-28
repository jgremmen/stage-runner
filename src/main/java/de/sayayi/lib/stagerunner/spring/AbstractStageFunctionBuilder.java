package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.util.StringUtils.hasLength;


public abstract class AbstractStageFunctionBuilder
{
  protected final StageFunctionAnnotation stageFunctionAnnotation;
  protected final ConversionService conversionService;
  protected final Map<String,ResolvableType> dataTypeMap;
  protected final ParameterNameDiscoverer parameterNameDiscoverer;


  protected AbstractStageFunctionBuilder(
      @NotNull Class<? extends Annotation> stageFunctionAnnotationType,
      @NotNull ConversionService conversionService,
      @NotNull Map<String,ResolvableType> dataTypeMap)
  {
    this.conversionService = conversionService;
    this.dataTypeMap = dataTypeMap;

    stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotationType);
    parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
  }


  public @NotNull <S extends Enum<S>> StageFunction<S> buildFor(Object bean, @NotNull Method method)
      throws ReflectiveOperationException
  {
    final int parameterCount = method.getParameterCount();
    final String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
    final NameWithQualifierAndType[] parameters = new NameWithQualifierAndType[parameterCount];
    final ResolvableType contextType = forClassWithGenerics(
        StageContext.class, stageFunctionAnnotation.getStageType());

    for(int p = 0; p < parameterCount; p++)
    {
      final MethodParameter methodParameter = new MethodParameter(method, p);
      final TypeDescriptor parameterType = new TypeDescriptor(methodParameter);
      final NameWithQualifier nameQualifier;

      if (parameterType.getResolvableType().isAssignableFrom(contextType))
        nameQualifier = new NameWithQualifier("$context", TypeQualifier.ASSIGNABLE);
      else
      {
        nameQualifier = findDataNameForParameter(
            methodParameter.getParameterAnnotation(Data.class), parameterType,
            parameterNames == null ? null : parameterNames[p]);
      }

      parameters[p] = new NameWithQualifierAndType(nameQualifier, parameterType);
    }

    return buildFor(bean, method, parameters);
  }


  protected abstract @NotNull <S extends Enum<S>> StageFunction<S> buildFor(
      Object bean,
      @NotNull Method method,
      @NotNull NameWithQualifierAndType[] parameters)
      throws ReflectiveOperationException;


  @Contract(pure = true)
  private @NotNull NameWithQualifier findDataNameForParameter(@Nullable Data dataAnnotation,
                                                              @NotNull TypeDescriptor parameterType,
                                                              String parameterName)
  {
    final List<NameWithQualifier> nameQualifiers = new ArrayList<>();
    ResolvableType dataType;

    if (dataAnnotation != null)
    {
      final String dataName = dataAnnotation.name();

      if ((dataType = dataTypeMap.get(dataName)) != null)
      {
        nameQualifiers.add(new NameWithQualifier(dataName,
            qualifyParameterTypeOrFail(parameterType, dataType)));
      }
    }

    if (hasLength(parameterName) && (dataType = dataTypeMap.get(parameterName)) != null)
    {
      nameQualifiers.add(new NameWithQualifier(parameterName,
          qualifyParameterTypeOrFail(parameterType, dataType)));
    }

    dataTypeMap.forEach((name,type) -> {
      TypeQualifier q = qualifyParameterType(parameterType, type);
      if (q != null)
        nameQualifiers.add(new NameWithQualifier(name, q));
    });

    if (nameQualifiers.isEmpty())
      throw new IllegalStateException("");

    NameWithQualifier nq1 = nameQualifiers.get(0);
    if (nameQualifiers.size() > 1)
    {
      nameQualifiers.sort(null);

      NameWithQualifier nq2 = nameQualifiers.get(1);

      if (!nq1.name.equals(nq2.name) && nq1.qualifier == nq2.qualifier)
      {
        throw new IllegalStateException("Ambiguous type for parameter " + parameterType +
            ", please specify @Data annotation");
      }
    }

    return nq1;
  }


  @Contract(pure = true)
  private @NotNull TypeQualifier qualifyParameterTypeOrFail(@NotNull TypeDescriptor parameterType,
                                                            @NotNull ResolvableType dataType)
  {
    final TypeQualifier qualifier = qualifyParameterType(parameterType, dataType);
    if (qualifier == null)
      throw new IllegalStateException("Unsupported parameter type: " + parameterType);

    return qualifier;
  }


  @Contract(pure = true)
  private TypeQualifier qualifyParameterType(@NotNull TypeDescriptor parameterType, @NotNull ResolvableType dataType)
  {
    final ResolvableType parameterResolvableType = parameterType.getResolvableType();

    if (parameterResolvableType.getType().equals(dataType.getType()))
      return TypeQualifier.IDENTICAL;

    if (parameterResolvableType.getRawClass() == Object.class)
      return TypeQualifier.ANYTHING;

    if (parameterResolvableType.isAssignableFrom(dataType))
      return TypeQualifier.ASSIGNABLE;

    if (conversionService.canConvert(
        new TypeDescriptor(dataType, null, null), parameterType))
      return TypeQualifier.CONVERTABLE;

    return null;
  }




  public static class NameWithQualifierAndType extends NameWithQualifier
  {
    protected final @NotNull TypeDescriptor type;


    protected NameWithQualifierAndType(@NotNull NameWithQualifier nameWithQualifier, @NotNull TypeDescriptor type)
    {
      super(nameWithQualifier.name, nameWithQualifier.qualifier);

      this.type = type;
    }


    public @NotNull TypeDescriptor getType() {
      return type;
    }


    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;
      if (!(o instanceof NameWithQualifierAndType))
        return false;

      final NameWithQualifierAndType that = (NameWithQualifierAndType)o;

      return name.equals(that.name) && qualifier == that.qualifier && type.equals(that.type);
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




  public static class NameWithQualifier implements Comparable<NameWithQualifier>
  {
    final @NotNull String name;
    final @NotNull TypeQualifier qualifier;


    protected NameWithQualifier(@NotNull String name, @NotNull TypeQualifier qualifier)
    {
      this.name = name;
      this.qualifier = qualifier;
    }


    public @NotNull String getName() {
      return name;
    }


    public @NotNull TypeQualifier getQualifier() {
      return qualifier;
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

      final NameWithQualifier that = (NameWithQualifier)o;

      return name.equals(that.name) && qualifier == that.qualifier;
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
}
