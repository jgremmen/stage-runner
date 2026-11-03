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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Objects;

import static de.sayayi.lib.stagerunner.StageFunctionConfigurer.DEFAULT_ORDER;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.springframework.util.StringUtils.hasLength;


/**
 * Descriptor for a user defined stage function annotation.
 * <p>
 * A stage function annotation is a custom annotation that marks a method as executable within a specific stage of a
 * stage runner. This class captures the relevant metadata of such an annotation: the annotation type itself, the enum
 * type used to identify stages, and the names of the annotation properties that provide the stage, order, description
 * and function name values.
 * <p>
 * Instances are created through {@link #buildFrom(Class)}, which inspects the annotation for the meta annotations
 * defined in {@link StageDefinition} and validates their return types.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class StageFunctionAnnotation
{
  /** Stage function annotation type. */
  private final @NotNull Class<? extends Annotation> annotationType;

  /** Stage enum type. */
  private final @NotNull Class<? extends Enum<?>> stageType;

  /** Name of the stage property. */
  private final @NotNull String stageProperty;

  /** Name of the order property, or {@code null} if not declared. */
  private final String orderProperty;

  /** Name of the description property, or {@code null} if not declared. */
  private final String descriptionProperty;

  /** Name of the function name property, or {@code null} if not declared. */
  private final String nameProperty;


  /**
   * Creates a new descriptor. Instances are created through {@link #buildFrom(Class)}.
   *
   * @param annotationType       stage function annotation type, not {@code null}
   * @param stageType            stage enum type, not {@code null}
   * @param stageProperty        name of the stage property, not {@code null}
   * @param orderProperty        name of the order property or {@code null}
   * @param descriptionProperty  name of the description property or {@code null}
   * @param nameProperty         name of the function name property or {@code null}
   */
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


  /**
   * Returns the stage function annotation type described by this instance.
   *
   * @return  the annotation type, never {@code null}
   */
  @Contract(pure = true)
  public @NotNull Class<? extends Annotation> getAnnotationType() {
    return annotationType;
  }


  /**
   * Returns the enum type used to identify the stages of the stage runner.
   *
   * @return  the stage enum type, never {@code null}
   */
  @Contract(pure = true)
  public @NotNull Class<? extends Enum<?>> getStageType() {
    return stageType;
  }


  /**
   * Returns the name of the annotation property that holds the stage value.
   *
   * @return  the stage property name, never {@code null}
   */
  @Contract(pure = true)
  public @NotNull String getStageProperty() {
    return stageProperty;
  }


  /**
   * Extracts the stage value from the given annotation attributes.
   *
   * @param annotationAttributes  attributes of a concrete stage function annotation instance, not {@code null}
   *
   * @return  the stage enum value, never {@code null}
   */
  @Contract(pure = true)
  public @NotNull Enum<?> getStage(@NotNull AnnotationAttributes annotationAttributes) {
    return annotationAttributes.getEnum(stageProperty);
  }


  /**
   * Returns the name of the annotation property that holds the order value, or {@code null} if the annotation does not
   * declare one.
   *
   * @return  the order property name or {@code null}
   */
  @Contract(pure = true)
  public String getOrderProperty() {
    return orderProperty;
  }


  /**
   * Extracts the order value from the given annotation attributes. If the annotation does not declare an order
   * property, {@link de.sayayi.lib.stagerunner.StageFunctionConfigurer#DEFAULT_ORDER} is returned.
   *
   * @param annotationAttributes  attributes of a concrete stage function annotation instance, not {@code null}
   *
   * @return  the order value
   */
  @Contract(pure = true)
  public int getOrder(@NotNull AnnotationAttributes annotationAttributes) {
    return orderProperty == null ? DEFAULT_ORDER : annotationAttributes.getNumber(orderProperty).intValue();
  }


  /**
   * Returns the name of the annotation property that holds the description value, or {@code null} if the annotation
   * does not declare one.
   *
   * @return  the description property name or {@code null}
   */
  @Contract(pure = true)
  public String getDescriptionProperty() {
    return descriptionProperty;
  }


  /**
   * Extracts the description value from the given annotation attributes.
   *
   * @param annotationAttributes  attributes of a concrete stage function annotation instance, not {@code null}
   *
   * @return  the description value, or {@code null} if the annotation does not declare a description property
   */
  @Contract(pure = true)
  public String getDescription(@NotNull AnnotationAttributes annotationAttributes) {
    return descriptionProperty == null ? null : annotationAttributes.getString(descriptionProperty);
  }


  /**
   * Returns the name of the annotation property that holds the function name value, or {@code null} if the annotation
   * does not declare one.
   *
   * @return  the name property name or {@code null}
   */
  @Contract(pure = true)
  public String getNameProperty() {
    return nameProperty;
  }


  /**
   * Extracts the function name value from the given annotation attributes. An empty name is treated as no name and
   * results in {@code null} being returned.
   *
   * @param annotationAttributes  attributes of a concrete stage function annotation instance, not {@code null}
   *
   * @return  the function name value, or {@code null} if not provided or empty
   */
  @Contract(pure = true)
  public String getName(@NotNull AnnotationAttributes annotationAttributes)
  {
    if (nameProperty == null)
      return null;

    var name = annotationAttributes.getString(nameProperty);
    return hasLength(name) ? name : null;
  }


  /**
   * Analyzes the given annotation type and builds a descriptor for it.
   * <p>
   * The annotation is scanned for methods that are marked with {@link StageDefinition.Stage @Stage},
   * {@link StageDefinition.Order @Order}, {@link StageDefinition.Description @Description} and
   * {@link StageDefinition.Name @Name}. A {@code @Stage} property is mandatory; all others are optional. Each meta
   * annotation may occur at most once and its property must have a compatible return type.
   * <p>
   * The annotation must have {@link java.lang.annotation.RetentionPolicy#RUNTIME runtime} retention. If it declares a
   * {@link Target @Target}, the target must include {@link java.lang.annotation.ElementType#METHOD METHOD} or, for use
   * as meta-annotation of a composed annotation, {@link java.lang.annotation.ElementType#ANNOTATION_TYPE
   * ANNOTATION_TYPE}.
   *
   * @param stageFunctionAnnotation  the annotation type to analyze, not {@code null}
   *
   * @return  a descriptor for the given annotation, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if the annotation is malformed, for example when it is not retained at
   *                                            run time, cannot be applied to methods, the mandatory {@code @Stage}
   *                                            property is missing, a meta annotation is declared more than once or
   *                                            a property has an unsupported return type
   */
  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  public static @NotNull StageFunctionAnnotation buildFrom(@NotNull Class<? extends Annotation> stageFunctionAnnotation)
  {
    // without runtime retention the annotation is invisible at run time and no stage function would ever be found
    final var retention = stageFunctionAnnotation.getAnnotation(Retention.class);
    if (retention == null || retention.value() != RUNTIME)
      throw new StageRunnerConfigurationException("Annotation must have runtime retention: " + stageFunctionAnnotation);

    // the annotation must be applicable to methods, either directly or as meta-annotation of a composed annotation
    final var target = stageFunctionAnnotation.getAnnotation(Target.class);
    if (target != null &&
        Arrays.stream(target.value()).noneMatch(elementType -> elementType == METHOD || elementType == ANNOTATION_TYPE))
    {
      throw new StageRunnerConfigurationException("Annotation must be applicable to methods or annotation types: " +
          stageFunctionAnnotation);
    }

    Class<?> stageType = null;
    String stagePropertyName = null;
    String orderPropertyName = null;
    String descriptionPropertyName = null;
    String namePropertyName = null;

    for(var method: stageFunctionAnnotation.getDeclaredMethods())
    {
      final var propertyName = method.getName();
      final var returnType = method.getReturnType();

      if (method.isAnnotationPresent(StageDefinition.Name.class))
      {
        if (namePropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Name annotation for " + method);

        if (returnType != String.class)
          throw new StageRunnerConfigurationException("Stage function name is not a String for " + method);

        namePropertyName = propertyName;
      }

      if (method.isAnnotationPresent(StageDefinition.Stage.class))
      {
        if (stagePropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Stage annotation for " + method);

        if (!Enum.class.isAssignableFrom(stageType = returnType) || stageType == Enum.class)
          throw new StageRunnerConfigurationException("Stage type is not an enum for " + method);

        stagePropertyName = propertyName;
      }

      if (method.isAnnotationPresent(StageDefinition.Order.class))
      {
        if (orderPropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Order annotation for " + method);

        if (returnType != int.class)
          throw new StageRunnerConfigurationException("Order type is not an int for " + method);

        orderPropertyName = propertyName;
      }

      if (method.isAnnotationPresent(StageDefinition.Description.class))
      {
        if (descriptionPropertyName != null)
          throw new StageRunnerConfigurationException("Duplicate @Description annotation for " + method);

        if (returnType != String.class)
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


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;

    if (!(o instanceof StageFunctionAnnotation that))
      return false;

    return
        annotationType == that.annotationType &&
        stageType == that.stageType &&
        stageProperty.equals(that.stageProperty) &&
        Objects.equals(orderProperty, that.orderProperty) &&
        Objects.equals(descriptionProperty, that.descriptionProperty) &&
        Objects.equals(nameProperty, that.nameProperty);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    var hash = (annotationType.hashCode() * 29 + stageType.hashCode()) * 29 + stageProperty.hashCode();

    if (orderProperty != null)
      hash = hash * 29 + orderProperty.hashCode();

    if (descriptionProperty != null)
      hash = hash * 29 + descriptionProperty.hashCode();

    if (nameProperty != null)
      hash = hash * 29 + nameProperty.hashCode();

    return hash;
  }


  /**
   * {@inheritDoc}
   */
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
