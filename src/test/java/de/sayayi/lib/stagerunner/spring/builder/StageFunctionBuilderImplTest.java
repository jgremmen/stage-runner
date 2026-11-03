/*
 * Copyright 2026 Jeroen Gremmen
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
import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.StageDef;
import de.sayayi.lib.stagerunner.spring.StageFunctionAnnotation;
import de.sayayi.lib.stagerunner.spring.annotation.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the resolution of stage function parameters by type in {@link StageFunctionBuilderImpl}.
 * <p>
 * Covered aspects are the selection of the strongest match independent of the data map iteration order, the detection
 * of ambiguous matches and the caching of generated classes, all through the public builder API. The equality of the
 * internal parameter bindings, which the class cache relies on, is tested directly.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage function builder - resolve parameter by type")
class StageFunctionBuilderImplTest
{
  private final StageFunctionBuilderImpl builder = new StageFunctionBuilderImpl(new DefaultConversionService());


  @Test
  @DisplayName("Best (strongest) match is picked regardless of data map iteration order")
  void bestMatchIsSelectedIndependentOfMapOrder() throws ReflectiveOperationException
  {
    // insertion order deliberately puts the weaker (ASSIGNABLE) match before the stronger (IDENTICAL) one;
    // a HashMap could easily iterate in this same order, which used to make the buggy code pick the wrong entry
    final var dataNameTypeMap = new LinkedHashMap<String,ResolvableType>();
    dataNameTypeMap.put("weak", ResolvableType.forClass(StringBuilder.class));
    dataNameTypeMap.put("strong", ResolvableType.forClass(CharSequence.class));

    assertEquals("strong", boundCharSequence(dataNameTypeMap));
  }


  @Test
  @DisplayName("Best (strongest) match is picked regardless of data map iteration order (reversed insertion)")
  void bestMatchIsSelectedIndependentOfMapOrderReversed() throws ReflectiveOperationException
  {
    // now insert the strongest match first: the outcome must not depend on this either
    final var dataNameTypeMap = new LinkedHashMap<String,ResolvableType>();
    dataNameTypeMap.put("strong", ResolvableType.forClass(CharSequence.class));
    dataNameTypeMap.put("weak", ResolvableType.forClass(StringBuilder.class));

    assertEquals("strong", boundCharSequence(dataNameTypeMap));
  }


  @Test
  @DisplayName("Two equally strong matches are reported as ambiguous")
  void ambiguousMatchesThrow() throws ReflectiveOperationException
  {
    final var dataNameTypeMap = new LinkedHashMap<String,ResolvableType>();

    dataNameTypeMap.put("a", ResolvableType.forClass(StringBuilder.class));
    dataNameTypeMap.put("b", ResolvableType.forClass(StringBuffer.class));

    final var method = TestTarget.class.getMethod("process", CharSequence.class);

    assertThrows(StageRunnerConfigurationException.class, () -> builder.createStageFunction(
        StageFunctionAnnotation.buildFrom(StageDef.class), dataNameTypeMap, method, new TestTarget()));
  }


  @Test
  @DisplayName("Generated stage function class is reused for the same method and parameter binding")
  void stageFunctionClassIsCached() throws ReflectiveOperationException
  {
    final var annotation = StageFunctionAnnotation.buildFrom(StageDef.class);
    final var method = TestTarget.class.getMethod("process", String.class);
    final var bean = new TestTarget();

    final var function1 = builder.createStageFunction(
        annotation, Map.of("value", ResolvableType.forClass(String.class)), method, bean);
    final var function2 = builder.createStageFunction(
        annotation, Map.of("value", ResolvableType.forClass(String.class)), method, bean);
    final var otherBinding = builder.createStageFunction(
        annotation, Map.of("value", ResolvableType.forClass(Integer.class)), method, bean);

    assertNotSame(function1, function2);
    assertSame(function1.getClass(), function2.getClass());
    assertNotSame(function1.getClass(), otherBinding.getClass());
  }


  @Test
  @DisplayName("Named data entry with an incompatible type is rejected as configuration error")
  void incompatibleDataType() throws ReflectiveOperationException
  {
    final var annotation = StageFunctionAnnotation.buildFrom(StageDef.class);
    final var dataNameTypeMap = Map.of(
        "value", ResolvableType.forClass(Thread.class),
        "other", ResolvableType.forClass(Thread.class));

    // matched by parameter name
    final var byName = TestTarget.class.getMethod("process", CharSequence.class);
    final var byNameException = assertThrows(StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(annotation, dataNameTypeMap, byName, new TestTarget()));

    assertEquals("Data 'value' of type java.lang.Thread cannot be supplied to parameter " +
        byName.getParameters()[0] + " of method " + byName, byNameException.getMessage());

    // matched by @Data annotation
    final var byAnnotation = TestTarget.class.getMethod("annotated", CharSequence.class);
    final var byAnnotationException = assertThrows(StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(annotation, dataNameTypeMap, byAnnotation, new TestTarget()));

    assertEquals("Data 'other' of type java.lang.Thread cannot be supplied to parameter " +
        byAnnotation.getParameters()[0] + " of method " + byAnnotation, byAnnotationException.getMessage());
  }


  @Test
  @DisplayName("@Data without name is rejected for a parameter which cannot be assigned the stage context")
  void unnamedDataOnNonContextParameter() throws ReflectiveOperationException
  {
    final var method = TestTarget.class.getMethod("unnamed", CharSequence.class);
    final var exception = assertThrows(
        StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(
            StageFunctionAnnotation.buildFrom(StageDef.class),
            Map.of("value", ResolvableType.forClass(String.class)),
            method, new TestTarget()));

    assertEquals("@Data without name is only allowed for a parameter which can be assigned the stage context: " +
        "parameter " + method.getParameters()[0] + " of method " + method, exception.getMessage());
  }


  @Test
  @DisplayName("@Data without name prevents a stage context parameter from being assigned the stage context")
  void unnamedDataOnContextParameter() throws ReflectiveOperationException
  {
    final var method = TestTarget.class.getMethod("unnamedContext", StageContext.class);

    // resolved like an unannotated data parameter; there is no matching data, so the binding fails
    final var exception = assertThrows(
        StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(StageFunctionAnnotation.buildFrom(StageDef.class), Map.of(),
            method, new TestTarget()));

    assertTrue(exception.getMessage().startsWith("Unknown data type for parameter "), exception.getMessage());
  }


  @Test
  @DisplayName("Parameter binding equality is symmetric and takes the parameter type into account")
  void parameterBindingEquality() throws ReflectiveOperationException
  {
    final var charSequenceType = parameterTypeOf("process", CharSequence.class);
    final var stringType = parameterTypeOf("process", String.class);

    final var nameWithQualifier = nameWithQualifier("value", StageFunctionBuilderImpl.TypeQualifier.IDENTICAL);
    final var binding = nameWithQualifierAndType(nameWithQualifier, charSequenceType);
    final var sameBinding = nameWithQualifierAndType(nameWithQualifier, charSequenceType);
    final var otherTypeBinding = nameWithQualifierAndType(nameWithQualifier, stringType);

    // symmetric, also between super and subclass
    assertNotEquals(nameWithQualifier, binding);
    assertNotEquals(binding, nameWithQualifier);

    assertEquals(binding, sameBinding);
    assertEquals(binding.hashCode(), sameBinding.hashCode());
    assertNotEquals(binding, otherTypeBinding);
  }


  @SuppressWarnings("SameParameterValue")
  private Object nameWithQualifier(String name, StageFunctionBuilderImpl.TypeQualifier qualifier)
      throws ReflectiveOperationException
  {
    final var constructor = Class
        .forName(StageFunctionBuilderImpl.class.getName() + "$NameWithQualifier")
        .getDeclaredConstructor(String.class, StageFunctionBuilderImpl.TypeQualifier.class);
    constructor.setAccessible(true);

    return constructor.newInstance(name, qualifier);
  }


  private Object nameWithQualifierAndType(Object nameWithQualifier, TypeDescriptor type)
      throws ReflectiveOperationException
  {
    final var constructor = Class
        .forName(StageFunctionBuilderImpl.class.getName() + "$NameWithQualifierAndType")
        .getDeclaredConstructor(nameWithQualifier.getClass(), TypeDescriptor.class);
    constructor.setAccessible(true);

    return constructor.newInstance(nameWithQualifier, type);
  }


  @SuppressWarnings("SameParameterValue")
  private TypeDescriptor parameterTypeOf(String methodName, Class<?> parameterClass) throws NoSuchMethodException {
    return new TypeDescriptor(new MethodParameter(TestTarget.class.getMethod(methodName, parameterClass), 0));
  }


  /**
   * Builds a stage function for {@link TestTarget#process(CharSequence)} with the given data types, runs it with data
   * values named after their entry ({@code "weak"} is a {@link StringBuilder}, all others are strings) and returns the
   * value bound to the parameter.
   */
  private String boundCharSequence(Map<String,ResolvableType> dataNameTypeMap) throws ReflectiveOperationException
  {
    final var bean = new TestTarget();
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    final var data = new HashMap<String,Object>();

    factory.addStageFunction(TestStage.INIT, builder.createStageFunction(
        StageFunctionAnnotation.buildFrom(StageDef.class), dataNameTypeMap,
        TestTarget.class.getMethod("process", CharSequence.class), bean));

    for(var name: dataNameTypeMap.keySet())
      data.put(name, "weak".equals(name) ? new StringBuilder(name) : name);

    assertTrue(factory.createRunner().run(data));

    return bean.value.toString();
  }




  @SuppressWarnings("unused")
  private static class TestTarget
  {
    CharSequence value;


    public void process(CharSequence value) {
      this.value = value;
    }


    public void process(String value) {
    }


    public void annotated(@Data("other") CharSequence value) {
    }


    public void unnamed(@Data CharSequence value) {
    }


    public void unnamedContext(@Data StageContext<TestStage> context) {
    }
  }
}
