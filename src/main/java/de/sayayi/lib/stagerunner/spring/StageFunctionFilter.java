package de.sayayi.lib.stagerunner.spring;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Decides whether a candidate stage function, discovered on a Spring bean, is registered with the stage runner
 * factory.
 * <p>
 * A filter is consulted by the {@link StageRunnerFactoryProcessor} once for every annotated stage function method
 * detected on a managed bean. Returning {@code false} silently skips the candidate, allowing callers to narrow
 * registration by bean type, stage, order or resolved function name (e.g. based on profile, environment or feature
 * flags).
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageFunctionFilter
{
  /**
   * Test whether the given stage function candidate must be registered.
   *
   * @param bean   bean instance declaring the stage function, not {@code null}
   * @param stage  stage at which the function would be executed, not {@code null}
   * @param order  execution order of the stage function within its stage
   * @param name   resolved stage function name, or {@code null} if the function is unnamed
   *
   * @return  {@code true} to register the stage function, {@code false} to skip it
   *
   * @param <B>  bean type
   * @param <S>  stage enumeration type
   */
  @Contract(pure = true)
  <B,S extends Enum<S>> boolean filter(@NotNull B bean, @NotNull S stage, int order, String name);
}
