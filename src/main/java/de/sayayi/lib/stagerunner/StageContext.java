package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;


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


  void abort();


  @Contract(pure = true)
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean isAborted();
}
