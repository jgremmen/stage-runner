package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

import static de.sayayi.lib.stagerunner.StageConfigurer.DEFAULT_ORDER;


public final class StageFunctionAnnotation
{
  private final @NotNull Class<? extends Annotation> annotationType;
  private final @NotNull Class<? extends Enum<?>> stageType;
  private final @NotNull String stageProperty;
  private final String orderProperty;
  private final String descriptionProperty;


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
  public @NotNull Class<? extends Annotation> getAnnotationType() {
    return annotationType;
  }


  @Contract(pure = true)
  public @NotNull Class<? extends Enum<?>> getStageType() {
    return stageType;
  }


  @Contract(pure = true)
  public @NotNull String getStageProperty() {
    return stageProperty;
  }


  @Contract(pure = true)
  public @NotNull Enum<?> getStage(@NotNull AnnotationAttributes annotationAttributes) {
    return annotationAttributes.getEnum(stageProperty);
  }


  @Contract(pure = true)
  public String getOrderProperty() {
    return orderProperty;
  }


  @Contract(pure = true)
  public int getOrder(@NotNull AnnotationAttributes annotationAttributes)
  {
    return orderProperty == null
        ? DEFAULT_ORDER
        : annotationAttributes.getNumber(orderProperty).intValue();
  }


  @Contract(pure = true)
  public String getDescriptionProperty() {
    return descriptionProperty;
  }


  @Contract(pure = true)
  public String getDescription(@NotNull AnnotationAttributes annotationAttributes) {
    return descriptionProperty == null ? null : annotationAttributes.getString(descriptionProperty);
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
          throw new StageRunnerException("Duplicate @Stage annotation for " + method);

        if (!Enum.class.isAssignableFrom(stageType = method.getReturnType()))
          throw new StageRunnerException("Stage type is not an enum for " + method);

        stagePropertyName = propertyName;
      }
      else if (method.isAnnotationPresent(StageDefinition.Order.class))
      {
        final Class<?> orderType = method.getReturnType();
        if (orderType != int.class && orderType != short.class && orderType != byte.class)
          throw new StageRunnerException("Order type is not an int or short for " + method);

        if (orderPropertyName != null)
          throw new StageRunnerException("Duplicate @Order annotation for " + method);

        orderPropertyName = propertyName;
      }
      else if (method.isAnnotationPresent(StageDefinition.Description.class))
      {
        final Class<?> descriptionType = method.getReturnType();
        if (descriptionType != String.class)
          throw new StageRunnerException("Description type is not a String for " + method);

        if (descriptionPropertyName != null)
          throw new StageRunnerException("Duplicate @Description annotation for " + method);

        descriptionPropertyName = propertyName;
      }
    }

    if (stagePropertyName == null)
      throw new StageRunnerException("No @Stage annotation found for " + stageFunctionAnnotation);

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
    int hash = (annotationType.hashCode() * 29 + stageType.hashCode()) * 29 + stageProperty.hashCode();

    if (orderProperty != null)
      hash = hash * 29 + orderProperty.hashCode();

    if (descriptionProperty != null)
      hash = hash * 29 + descriptionProperty.hashCode();

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
