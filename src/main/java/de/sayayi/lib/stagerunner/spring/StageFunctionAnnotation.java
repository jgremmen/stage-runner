package de.sayayi.lib.stagerunner.spring;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;


public final class StageFunctionAnnotation
{
  final @NotNull Class<? extends Annotation> annotationType;
  final @NotNull Class<? extends Enum<?>> stageType;
  final @NotNull String stageProperty;
  final String orderProperty;
  final String descriptionProperty;


  private StageFunctionAnnotation(@NotNull Class<? extends Annotation> annotationType,
                                  @NotNull Class<? extends Enum<?>> stageType,
                                  @NotNull String stageProperty,
                                  String orderProperty,
                                  String descriptionProperty)
  {
    this.annotationType = annotationType;
    this.stageType = stageType;
    this.stageProperty = stageProperty;
    this.orderProperty = orderProperty;
    this.descriptionProperty = descriptionProperty;
  }


  @Contract(pure = true)
  public static @NotNull StageFunctionAnnotation buildFrom(
      @NotNull Class<? extends Annotation> stageFunctionAnnotation)
  {
    Class<?> stageType = null;
    String stagePropertyName = null;
    String orderPropertyName = null;
    String descriptionPropertyName = null;

    for(final Method method: stageFunctionAnnotation.getDeclaredMethods())
    {
      final String propertyName = method.getName();

      if (method.isAnnotationPresent(StageDefinition.Stage.class))
      {
        if (stagePropertyName != null)
          throw new IllegalArgumentException("Duplicate @Stage annotation for " + method);

        if (!Enum.class.isAssignableFrom(stageType = method.getReturnType()))
          throw new IllegalArgumentException("Stage type is not an enum for " + method);

        stagePropertyName = propertyName;
      }
      else if (method.isAnnotationPresent(StageDefinition.Order.class))
      {
        final Class<?> orderType = method.getReturnType();
        if (orderType != int.class && orderType != short.class && orderType != byte.class)
          throw new IllegalArgumentException("Order type is not an int or short for " + method);

        if (orderPropertyName != null)
          throw new IllegalArgumentException("Duplicate @Order annotation for " + method);

        orderPropertyName = propertyName;
      }
      else if (method.isAnnotationPresent(StageDefinition.Description.class))
      {
        final Class<?> descriptionType = method.getReturnType();
        if (descriptionType != String.class)
          throw new IllegalArgumentException("Description type is not a String for " + method);

        if (descriptionPropertyName != null)
          throw new IllegalArgumentException("Duplicate @Description annotation for " + method);

        descriptionPropertyName = propertyName;
      }
    }

    if (stagePropertyName == null)
      throw new IllegalArgumentException("No @Stage annotation found for " + stageFunctionAnnotation);

    //noinspection unchecked
    return new StageFunctionAnnotation(
        stageFunctionAnnotation,
        (Class<? extends Enum<?>>)stageType,
        stagePropertyName,
        orderPropertyName,
        descriptionPropertyName);
  }


  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (!(o instanceof StageFunctionAnnotation))
      return false;

    final StageFunctionAnnotation that = (StageFunctionAnnotation)o;

    return
        annotationType == that.annotationType &&
        stageType == that.stageType &&
        stageProperty.equals(that.stageProperty) &&
        Objects.equals(orderProperty, that.orderProperty) &&
        Objects.equals(descriptionProperty, that.descriptionProperty);
  }


  @Override
  public int hashCode()
  {
    int hash = (annotationType.hashCode() * 31 + stageType.hashCode()) * 31 + stageProperty.hashCode();

    if (orderProperty != null)
      hash = hash * 31 + orderProperty.hashCode();

    if (descriptionProperty != null)
      hash = hash * 31 + descriptionProperty.hashCode();

    return hash;
  }


  @Override
  public String toString()
  {
    final StringBuilder s = new StringBuilder(getClass().getSimpleName())
        .append("(annotation=").append(annotationType.getSimpleName())
        .append(",stageType=").append(stageType.getSimpleName())
        .append(",stage=").append(stageProperty).append("()");

    if (orderProperty != null)
      s.append(",order=").append(orderProperty).append("()");
    if (descriptionProperty != null)
      s.append(",description=").append(descriptionProperty).append("()");

    return s.append(')').toString();
  }
}
