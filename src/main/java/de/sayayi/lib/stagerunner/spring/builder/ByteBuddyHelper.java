package de.sayayi.lib.stagerunner.spring.builder;

import net.bytebuddy.description.type.TypeDescription;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;


public final class ByteBuddyHelper
{
  @Contract(pure = true)
  public static @NotNull TypeDescription typeDescription(@NotNull Class<?> type) {
    return TypeDescription.ForLoadedType.of(type);
  }


  @Contract(pure = true)
  public static @NotNull TypeDescription.Generic parameterizedType(@NotNull Class<?> rawType, Type... parameter) {
    return TypeDescription.Generic.Builder.parameterizedType(rawType, parameter).build();
  }
}
