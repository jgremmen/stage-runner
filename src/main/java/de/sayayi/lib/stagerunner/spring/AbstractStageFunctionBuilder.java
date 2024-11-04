package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.sayayi.lib.stagerunner.spring.AbstractStageFunctionBuilder.TypeQualifier.CONVERTABLE;
import static org.springframework.core.ResolvableType.forClassWithGenerics;
import static org.springframework.util.StringUtils.hasLength;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public abstract class AbstractStageFunctionBuilder
{
  protected final StageFunctionAnnotation stageFunctionAnnotation;
  protected final ConversionService conversionService;
  protected final Map<String,ResolvableType> dataNameTypeMap;


  protected AbstractStageFunctionBuilder(
      @NotNull Class<? extends Annotation> stageFunctionAnnotationType,
      @NotNull ConversionService conversionService,
      @NotNull Map<String,ResolvableType> dataNameTypeMap)
  {
    this.conversionService = conversionService;
    this.dataNameTypeMap = dataNameTypeMap;

    stageFunctionAnnotation = StageFunctionAnnotation.buildFrom(stageFunctionAnnotationType);
  }


  public @NotNull <S extends Enum<S>> StageFunction<S> buildFor(Object bean, @NotNull Method method)
      throws ReflectiveOperationException
  {
    final Parameter[] methodParameters = method.getParameters();
    final NameWithQualifierAndType[] parameters = new NameWithQualifierAndType[methodParameters.length];
    final ResolvableType stageContextType = forClassWithGenerics(
        StageContext.class, stageFunctionAnnotation.getStageType());

    for(int p = 0; p < methodParameters.length; p++)
    {
      final TypeDescriptor parameterType = new TypeDescriptor(new MethodParameter(method, p));

      parameters[p] = new NameWithQualifierAndType(
          parameterType.getResolvableType().isAssignableFrom(stageContextType)
              ? new NameWithQualifier("$context", TypeQualifier.ASSIGNABLE)
              : findNameWithQualifier(methodParameters[p], parameterType),
          parameterType);
    }

    return buildFor(bean, method, parameters);
  }


  protected abstract @NotNull <S extends Enum<S>> StageFunction<S> buildFor(
      Object bean,
      @NotNull Method method,
      @NotNull NameWithQualifierAndType[] parameters)
      throws ReflectiveOperationException;


  @Contract(pure = true)
  private @NotNull NameWithQualifier findNameWithQualifier(@NotNull Parameter parameter,
                                                           @NotNull TypeDescriptor parameterType)
  {
    NameWithQualifier nameWithQualifier;

    if ((nameWithQualifier = findNameWithQualifierByParameterName(parameter, parameterType)) == null &&
        (nameWithQualifier = findNameWithQualifierByParameterType(parameter, parameterType)) == null)
    {
      throw new StageRunnerConfigurationException("Unknown data type for parameter " + parameter +
          "; please specify @Data annotation and/or extend the conversion service");
    }

    return nameWithQualifier;
  }


  @Contract(pure = true)
  private NameWithQualifier findNameWithQualifierByParameterName(@NotNull Parameter parameter,
                                                                 @NotNull TypeDescriptor parameterType)
  {
    ResolvableType dataType;

    final Data dataAnnotation = parameter.getAnnotation(Data.class);
    if (dataAnnotation != null)
    {
      final String dataName = dataAnnotation.value();
      if (!hasLength(dataName))
        throw new StageRunnerConfigurationException("@Data name must not be empty for parameter " + parameter);

      if ((dataType = dataNameTypeMap.get(dataName)) != null)
        return new NameWithQualifier(dataName, qualifyParameterTypeOrFail(parameterType, dataType));

      throw new StageRunnerConfigurationException("Unknown @Data name '" + dataName + "' for parameter " + parameter);
    }

    final String parameterName = parameter.getName();
    if (hasLength(parameterName) && (dataType = dataNameTypeMap.get(parameterName)) != null)
      return new NameWithQualifier(parameterName, qualifyParameterTypeOrFail(parameterType, dataType));

    return null;
  }


  @Contract(pure = true)
  private NameWithQualifier findNameWithQualifierByParameterType(@NotNull Parameter parameter,
                                                                 @NotNull TypeDescriptor parameterType)
  {
    final List<NameWithQualifier> nameQualifiers = new ArrayList<>();

    dataNameTypeMap.forEach((name, type) -> {
      TypeQualifier q = qualifyParameterType(parameterType, type);
      if (q != null)
      {
        final NameWithQualifier nwq = new NameWithQualifier(name, q);
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

        final NameWithQualifier nwq2 = nameQualifiers.get(1);

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
      return CONVERTABLE;

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


    @Contract(pure = true)
    public @NotNull TypeDescriptor getType() {
      return type;
    }


    @Contract(pure = true)
    public boolean isConvertableQualifier() {
      return qualifier == CONVERTABLE;
    }


    @Override
    public boolean equals(Object o)
    {
      if (this == o)
        return true;
      if (!(o instanceof NameWithQualifierAndType))
        return false;

      final NameWithQualifierAndType that = (NameWithQualifierAndType)o;

      return qualifier == that.qualifier &&
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
}
