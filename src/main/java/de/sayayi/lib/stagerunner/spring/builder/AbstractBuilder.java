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

import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.utility.RandomString;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;


/**
 * Common base class for the ByteBuddy based builders in this package. It provides a shared random string generator
 * for producing unique generated class names, a lookup for defining generated classes in the package of a host class,
 * as well as small helpers for describing loaded and parameterized types.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@ApiStatus.Internal
abstract class AbstractBuilder
{
  /**
   * Random string generator used to make generated class names unique.
   *
   * @since 0.3.2
   */
  protected final RandomString randomString = new RandomString(5);


  /**
   * Returns a lookup with full privilege access on {@code hostClass}.
   * <p>
   * Generated classes are defined through this lookup, so they are placed in the package, class loader and module of
   * {@code hostClass}. This gives the generated class access to package-private types of that package and avoids
   * injecting classes into foreign packages or class loaders.
   *
   * @param hostClass  class in whose package the generated class is defined, not {@code null}
   *
   * @return  private lookup on {@code hostClass}, never {@code null}
   *
   * @throws StageRunnerConfigurationException  if the package of {@code hostClass} is not open to this library
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected static @NotNull MethodHandles.Lookup hostLookup(@NotNull Class<?> hostClass)
  {
    final var lookup = MethodHandles.lookup();
    final var libraryModule = lookup.lookupClass().getModule();

    libraryModule.addReads(hostClass.getModule());

    try {
      return MethodHandles.privateLookupIn(hostClass, lookup);
    } catch(IllegalAccessException ex) {
      throw new StageRunnerConfigurationException("failed to access package " + hostClass.getPackageName() +
          " for generating a class for " + hostClass.getName() + "; the package must be open to " + libraryModule, ex);
    }
  }


  /**
   * Creates the exception reported when a generated class cannot be defined or linked in the class loader of
   * {@code hostClass}. This happens if that class loader cannot load the stage runner library or Spring, for example if
   * {@code hostClass} is located in a parent class loader of a hierarchical class loader setup.
   *
   * @param message    message describing the failed operation, not {@code null}
   * @param hostClass  class in whose class loader the generated class is defined, not {@code null}
   * @param error      linkage error, not {@code null}
   *
   * @return  configuration exception naming the class loader, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected static @NotNull StageRunnerConfigurationException linkageFailure(@NotNull String message,
                                                                            @NotNull Class<?> hostClass,
                                                                            @NotNull LinkageError error)
  {
    return new StageRunnerConfigurationException(message + "; the class loader " + hostClass.getClassLoader() +
        " of " + hostClass.getName() + " must be able to load the stage runner library and Spring", error);
  }


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
   * @param parameter  the type arguments to apply, not {@code null}
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
    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns {@code instrumentedType} unchanged.
     */
    @Override
    public @NotNull InstrumentedType prepare(@NotNull InstrumentedType instrumentedType) {
      return instrumentedType;
    }
  }
}
