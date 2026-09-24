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

import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
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
    final var parameterType = parameterTypeOf("process", CharSequence.class);

    // insertion order deliberately puts the weaker (ASSIGNABLE) match before the stronger (IDENTICAL) one;
    // a HashMap could easily iterate in this same order, which used to make the buggy code pick the wrong entry
    final var dataNameTypeMap = new LinkedHashMap<String,ResolvableType>();
    dataNameTypeMap.put("weak", ResolvableType.forClass(StringBuilder.class));
    dataNameTypeMap.put("strong", ResolvableType.forClass(CharSequence.class));

    final var nameWithQualifier = invokeFindNameWithQualifierByParameterType(parameterType, dataNameTypeMap);

    assertEquals("strong", nameField(nameWithQualifier));
  }


  @Test
  @DisplayName("Best (strongest) match is picked regardless of data map iteration order (reversed insertion)")
  void bestMatchIsSelectedIndependentOfMapOrderReversed() throws ReflectiveOperationException
  {
    final var parameterType = parameterTypeOf("process", CharSequence.class);

    // now insert the strongest match first: the outcome must not depend on this either
    final var dataNameTypeMap = new LinkedHashMap<String,ResolvableType>();
    dataNameTypeMap.put("strong", ResolvableType.forClass(CharSequence.class));
    dataNameTypeMap.put("weak", ResolvableType.forClass(StringBuilder.class));

    final var nameWithQualifier = invokeFindNameWithQualifierByParameterType(parameterType, dataNameTypeMap);

    assertEquals("strong", nameField(nameWithQualifier));
  }


  @Test
  @DisplayName("Two equally strong matches are reported as ambiguous")
  void ambiguousMatchesThrow() throws ReflectiveOperationException
  {
    final var parameterType = parameterTypeOf("process", CharSequence.class);
    final var dataNameTypeMap = new LinkedHashMap<String,ResolvableType>();

    dataNameTypeMap.put("a", ResolvableType.forClass(StringBuilder.class));
    dataNameTypeMap.put("b", ResolvableType.forClass(StringBuffer.class));

    final var method = StageFunctionBuilderImpl.class.getDeclaredMethod(
        "findNameWithQualifierByParameterType", Parameter.class, TypeDescriptor.class, Map.class);
    method.setAccessible(true);

    final var parameter = TestTarget.class.getMethod("process", CharSequence.class).getParameters()[0];
    final var exception = assertThrows(java.lang.reflect.InvocationTargetException.class,
        () -> method.invoke(builder, parameter, parameterType, dataNameTypeMap));

    assertEquals(StageRunnerConfigurationException.class, exception.getCause().getClass());
  }


  private TypeDescriptor parameterTypeOf(String methodName, Class<?> parameterClass) throws NoSuchMethodException {
    return new TypeDescriptor(new MethodParameter(TestTarget.class.getMethod(methodName, parameterClass), 0));
  }


  private Object invokeFindNameWithQualifierByParameterType(
      TypeDescriptor parameterType, Map<String,ResolvableType> dataNameTypeMap) throws ReflectiveOperationException
  {
    final var method = StageFunctionBuilderImpl.class.getDeclaredMethod(
        "findNameWithQualifierByParameterType", Parameter.class, TypeDescriptor.class, Map.class);
    method.setAccessible(true);

    final var parameter = TestTarget.class.getMethod("process", CharSequence.class).getParameters()[0];

    return method.invoke(builder, parameter, parameterType, dataNameTypeMap);
  }


  private String nameField(Object nameWithQualifier) throws ReflectiveOperationException
  {
    final var field = nameWithQualifier.getClass().getDeclaredField("name");
    field.setAccessible(true);

    return (String)field.get(nameWithQualifier);
  }




  @SuppressWarnings("unused")
  private static class TestTarget
  {
    public void process(CharSequence value) {
    }
  }
}
