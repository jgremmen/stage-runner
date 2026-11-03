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

import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spring.annotation.StageDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests {@link StageFunctionAnnotation#buildFrom(Class)}.
 * <p>
 * Covers the detection of the stage definition properties of a fully and a minimally specified stage function
 * annotation, and the rejection of malformed annotations.
 *
 * @since 0.3.0
 */
@DisplayName("Stage function annotation")
class StageFunctionAnnotationTest
{
  @Test
  @DisplayName("Fully qualified function annotation")
  void fqAnnotation()
  {
    final StageFunctionAnnotation sfa = StageFunctionAnnotation.buildFrom(StageDef.class);

    assertEquals(StageDef.class, sfa.getAnnotationType());
    assertEquals(TestStage.class, sfa.getStageType());
    assertEquals("scope", sfa.getNameProperty());
    assertEquals("stage", sfa.getStageProperty());
    assertEquals("priority", sfa.getOrderProperty());
    assertEquals("comment", sfa.getDescriptionProperty());
  }


  @Test
  @DisplayName("Annotation with a stage property only")
  void stageOnly()
  {
    final var sfa = StageFunctionAnnotation.buildFrom(StageOnly.class);

    assertEquals(TestStage.class, sfa.getStageType());
    assertEquals("value", sfa.getStageProperty());
    assertNull(sfa.getNameProperty());
    assertNull(sfa.getOrderProperty());
    assertNull(sfa.getDescriptionProperty());
  }


  @Test
  @DisplayName("Malformed function annotations are rejected")
  void malformedAnnotations()
  {
    assertRejected(NoStage.class, "No @Stage annotation found for ");
    assertRejected(StageNotEnum.class, "Stage type is not an enum for ");
    assertRejected(DuplicateStage.class, "Duplicate @Stage annotation for ");
    assertRejected(OrderNotInt.class, "Order type is not an int for ");
    assertRejected(NameNotString.class, "Stage function name is not a String for ");
    assertRejected(DescriptionNotString.class, "Description type is not a String for ");
    assertRejected(NoRetention.class, "Annotation must have runtime retention: ");
    assertRejected(ClassRetention.class, "Annotation must have runtime retention: ");
    assertRejected(SourceRetention.class, "Annotation must have runtime retention: ");
    assertRejected(FieldTarget.class, "Annotation must be applicable to methods or annotation types: ");
  }


  @Test
  @DisplayName("Annotation applicable to annotation types only is accepted as meta-annotation")
  void annotationTypeTarget()
  {
    final var sfa = StageFunctionAnnotation.buildFrom(MetaOnly.class);

    assertEquals(TestStage.class, sfa.getStageType());
    assertEquals("stage", sfa.getStageProperty());
  }


  private static void assertRejected(Class<? extends Annotation> annotationType, String messagePrefix)
  {
    final var exception = assertThrows(StageRunnerConfigurationException.class,
        () -> StageFunctionAnnotation.buildFrom(annotationType));

    assertTrue(exception.getMessage().startsWith(messagePrefix), exception.getMessage());
  }




  @Retention(RUNTIME)
  @interface StageOnly {
    @StageDefinition.Stage TestStage value();
  }




  @Retention(RUNTIME)
  @SuppressWarnings("unused")
  @interface NoStage {
    @StageDefinition.Order int order();
  }




  @Retention(RUNTIME)
  @interface StageNotEnum {
    @StageDefinition.Stage String stage();
  }




  @Retention(RUNTIME)
  @SuppressWarnings("unused")
  @interface DuplicateStage {
    @StageDefinition.Stage TestStage stage1();
    @StageDefinition.Stage TestStage stage2();
  }




  @Retention(RUNTIME)
  @SuppressWarnings("unused")
  @interface OrderNotInt {
    @StageDefinition.Stage TestStage stage();
    @StageDefinition.Order long order();
  }




  @Retention(RUNTIME)
  @interface NameNotString {
    @StageDefinition.Stage TestStage stage();
    @StageDefinition.Name int name();
  }




  @Retention(RUNTIME)
  @SuppressWarnings("unused")
  @interface DescriptionNotString {
    @StageDefinition.Stage TestStage stage();
    @StageDefinition.Description int description();
  }




  @interface NoRetention {
    @StageDefinition.Stage TestStage stage();
  }




  @Retention(CLASS)
  @interface ClassRetention {
    @StageDefinition.Stage TestStage stage();
  }




  @Retention(SOURCE)
  @interface SourceRetention {
    @StageDefinition.Stage TestStage stage();
  }




  @Target(FIELD)
  @Retention(RUNTIME)
  @interface FieldTarget {
    @StageDefinition.Stage TestStage stage();
  }




  @Target(ANNOTATION_TYPE)
  @Retention(RUNTIME)
  @interface MetaOnly {
    @StageDefinition.Stage TestStage stage();
  }
}
