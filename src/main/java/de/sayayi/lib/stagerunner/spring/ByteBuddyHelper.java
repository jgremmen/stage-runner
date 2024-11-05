package de.sayayi.lib.stagerunner.spring;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;


final class ByteBuddyHelper
{
  static final @NotNull StackManipulation SWAP = new StackManipulation.AbstractBase() {
    @Override
    public @NotNull Size apply(@NotNull MethodVisitor methodVisitor, @NotNull Context context) {
      methodVisitor.visitInsn(Opcodes.SWAP);
      return Size.ZERO;
    }
  };


  @Contract(pure = true)
  static @NotNull TypeDescription typeDescription(@NotNull Class<?> type) {
    return TypeDescription.ForLoadedType.of(type);
  }


  @Contract(pure = true)
  static @NotNull TypeDescription.Generic parameterizedType(@NotNull Class<?> rawType, Type... parameter) {
    return TypeDescription.Generic.Builder.parameterizedType(rawType, parameter).build();
  }




  abstract static class AbstractImplementation implements Implementation
  {
    @Override
    public @NotNull InstrumentedType prepare(@NotNull InstrumentedType instrumentedType) {
      return instrumentedType;
    }
  }
}
