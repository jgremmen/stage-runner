package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public interface StageContext<S extends Enum<S>> extends StageConfigurer<S>
{
  /**
   * Returns the stage data associated with {@code name}.
   *
   * @param name  data name, not {@code null}
   *
   * @return  data value for name {@code name} or null if no data is available
   *
   * @param <T>  data type
   *
   * @see StageRunner#run(Map)
   */
  @Contract(pure = true)
  <T> T getData(@NotNull String name);


  /**
   * Returns the state being currently processed.
   *
   * @return  state currently being processed, never {@code null}
   *
   * @throws IllegalStateException  if the stage runner hasn't started yet or has finished
   */
  @Contract(pure = true)
  @NotNull S getCurrentStage();


  /**
   * Returns a set with stages which have been processed. The current stage is never part of this
   * collection.
   *
   * @return  set with processed stages, never {@code null}
   */
  @Contract(pure = true)
  @NotNull Set<S> getProcessedStages();


  /**
   * Returns a set with the remaining stages. The current stage is never part of this collection.
   *
   * @return  set with remaining stages, never {@code null}
   */
  @Contract(pure = true)
  @NotNull Set<S> getRemainingStages();


  /**
   * Abort stage context running.
   */
  void abort();


  @Contract(pure = true)
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean isAborted();


  /**
   *
   * @param name  name of the stage function to enabler
   *
   * @return  {@code true} if a stage function with {@code name} exists and has been enabled by this invocation,
   *          {@code false} otherwise
   *
   * @since 0.3.0
   */
  default boolean enableNamedStageFunction(@NotNull String name) {
    return !enableNamedStageFunctions(n -> n.equals(name)).isEmpty();
  }


  /**
   *
   * @param nameFilter  name filter predicate. A stage function is added if and only if the predicate
   *                    ({@link Predicate#test(Object)}) returns {@code true}
   *
   * @return  a set with the stage function names, which have been enabled, never {@code null}
   *
   * @since 0.3.0
   */
  @NotNull Set<String> enableNamedStageFunctions(@NotNull Predicate<String> nameFilter);
}
