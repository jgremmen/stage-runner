package de.sayayi.lib.stagerunner.spring.builder;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
abstract class AbstractBuilder
{
  @Contract(pure = true)
  protected static @NotNull TypeDescription typeDescription(@NotNull Class<?> type) {
    return TypeDescription.ForLoadedType.of(type);
  }


  @Contract(pure = true)
  protected static @NotNull TypeDescription.Generic parameterizedType(@NotNull Class<?> rawType, Type... parameter) {
    return TypeDescription.Generic.Builder.parameterizedType(rawType, parameter).build();
  }




  protected static abstract class AbstractImplementation implements Implementation
  {
    @Override
    public @NotNull InstrumentedType prepare(@NotNull InstrumentedType instrumentedType) {
      return instrumentedType;
    }
  }
}
