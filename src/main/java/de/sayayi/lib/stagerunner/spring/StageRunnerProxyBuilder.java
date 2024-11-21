package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageRunnerProxyBuilder
{
  @Contract(pure = true)
  <R,S extends Enum<S>> @NotNull R createProxy(@NotNull Class<S> stageType,
                                               @NotNull Class<R> stageRunnerInterfaceType,
                                               @NotNull Method stageRunnerInterfaceMethod,
                                               @NotNull String[] dataNames,
                                               @NotNull StageRunnerFactory<S> stageRunnerFactory);
}
