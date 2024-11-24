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

import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spring.annotation.StageDefinition;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotationAttributes;

import java.lang.annotation.Annotation;
import java.util.Objects;

import static de.sayayi.lib.stagerunner.StageFunctionConfigurer.DEFAULT_ORDER;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class StageFunctionAnnotation
{
  private final @NotNull Class<? extends Annotation> annotationType;
  private final @NotNull Class<? extends Enum<?>> stageType;
  private final @NotNull String stageProperty;
  private final String orderProperty;
  private final String descriptionProperty;
  private final String nameProperty;


  private StageFunctionAnnotation(@NotNull Class<? extends Annotation> annotationType,
                                  @NotNull Class<? extends Enum<?>> stageType,
                                  @NotNull String stageProperty,
                                  String orderProperty,
                                  String descriptionProperty,
                                  String nameProperty)
  {
    this.annotationType = annotationType;
    this.stageType = stageType;
    this.stageProperty = stageProperty;
    this.orderProperty = orderProperty;
    this.descriptionProperty = descriptionProperty;
    this.nameProperty = nameProperty;
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
  public String getNameProperty() {
    return nameProperty;
  }


  @Contract(pure = true)
  public String getName(@NotNull AnnotationAttributes annotationAttributes)
  {
    if (nameProperty == null)
      return null;

    var name = annotationAttributes.getString(nameProperty);
    return name.isEmpty() ? null : name;
  }


  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  public static @NotNull StageFunctionAnnotation buildFrom(
      @NotNull Class<? extends Annotation> stageFunctionAnnotation)
  {
    Class<?> stageType = null;
    String stagePropertyName = null;
    String orderPropertyName = null;
    String descriptionPropertyName = null;
    String namePropertyName = null;

    for(var method: stageFunctionAnnotation.getDeclaredMethods())
    {
      var propertyName = method.getName();

      if (method.isAnnotationPresent(StageDefinition.Name.class))
      {
        if (namePropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Name annotation for " + method);

        if (method.getReturnType() != String.class)
          throw new StageRunnerConfigurationException("Stage function name is not a String for " + method);

        namePropertyName = propertyName;
      }

      if (method.isAnnotationPresent(StageDefinition.Stage.class))
      {
        if (stagePropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Stage annotation for " + method);

        if (!Enum.class.isAssignableFrom(stageType = method.getReturnType()) || stageType == Enum.class)
          throw new StageRunnerConfigurationException("Stage type is not an enum for " + method);

        stagePropertyName = propertyName;
      }

      if (method.isAnnotationPresent(StageDefinition.Order.class))
      {
        if (orderPropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Order annotation for " + method);

        var orderType = method.getReturnType();
        if (orderType != int.class && orderType != short.class)
          throw new StageRunnerConfigurationException("Order type is not an int or short for " + method);

        orderPropertyName = propertyName;
      }

      if (method.isAnnotationPresent(StageDefinition.Description.class))
      {
        if (descriptionPropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Description annotation for " + method);

        var descriptionType = method.getReturnType();
        if (descriptionType != String.class)
          throw new StageRunnerConfigurationException("Description type is not a String for " + method);

        descriptionPropertyName = propertyName;
      }
    }

    if (stagePropertyName == null)
      throw new StageRunnerConfigurationException("No @Stage annotation found for " + stageFunctionAnnotation);

    return new StageFunctionAnnotation(
        stageFunctionAnnotation,
        (Class<? extends Enum<?>>)stageType,
        stagePropertyName,
        orderPropertyName,
        descriptionPropertyName,
        namePropertyName);
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
        Objects.equals(descriptionProperty, that.descriptionProperty) &&
        Objects.equals(nameProperty, that.nameProperty);
  }


  @Override
  public int hashCode()
  {
    int hash = (annotationType.hashCode() * 29 + stageType.hashCode()) * 29 + stageProperty.hashCode();

    if (orderProperty != null)
      hash = hash * 29 + orderProperty.hashCode();

    if (descriptionProperty != null)
      hash = hash * 29 + descriptionProperty.hashCode();

    if (nameProperty != null)
      hash = hash * 29 + nameProperty.hashCode();

    return hash;
  }


  @Override
  public String toString()
  {
    var s = new StringBuilder(getClass().getSimpleName())
        .append("(annotation=").append(annotationType.getSimpleName())
        .append(",stageType=").append(stageType.getSimpleName())
        .append(",stage=").append(stageProperty).append("()");

    if (orderProperty != null)
      s.append(",order=").append(orderProperty).append("()");
    if (descriptionProperty != null)
      s.append(",description=").append(descriptionProperty).append("()");
    if (nameProperty != null)
      s.append(",name=").append(nameProperty).append("()");

    return s.append(')').toString();
  }
}
