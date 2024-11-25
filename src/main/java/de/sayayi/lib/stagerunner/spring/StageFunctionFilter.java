package de.sayayi.lib.stagerunner.spring;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageFunctionFilter
{
  @Contract(pure = true)
  <B,S extends Enum<S>> boolean filter(@NotNull B bean, @NotNull S stage, int order, String name);
}
