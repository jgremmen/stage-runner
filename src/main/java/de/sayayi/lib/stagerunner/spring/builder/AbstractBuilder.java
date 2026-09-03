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

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.utility.RandomString;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;


/**
 * Common base class for the ByteBuddy based builders in this package. It provides a shared random string generator
 * for producing unique generated class names as well as small helpers for describing loaded and parameterized types.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
abstract class AbstractBuilder
{
  /** Random string generator used to make generated class names unique. */
  protected final RandomString randomString = new RandomString(5);


  /**
   * Returns a {@link TypeDescription} for the given loaded {@code type}.
   *
   * @param type  the class to describe, not {@code null}
   *
   * @return  a type description for {@code type}, never {@code null}
   */
  @Contract(pure = true)
  protected static @NotNull TypeDescription typeDescription(@NotNull Class<?> type) {
    return TypeDescription.ForLoadedType.of(type);
  }


  /**
   * Returns a generic {@link TypeDescription.Generic} that represents {@code rawType} parameterized with the given
   * type arguments.
   *
   * @param rawType    the raw type to parameterize, not {@code null}
   * @param parameter  the type arguments to apply
   *
   * @return  a parameterized generic type description, never {@code null}
   */
  @Contract(pure = true)
  protected static @NotNull TypeDescription.Generic parameterizedType(@NotNull Class<?> rawType, Type... parameter) {
    return TypeDescription.Generic.Builder.parameterizedType(rawType, parameter).build();
  }




  /**
   * Base class for ByteBuddy {@link Implementation} instances used by the builders in this package that do not need
   * to contribute anything to the instrumented type during preparation.
   */
  protected static abstract class AbstractImplementation implements Implementation
  {
    @Override
    public @NotNull InstrumentedType prepare(@NotNull InstrumentedType instrumentedType) {
      return instrumentedType;
    }
  }
}
