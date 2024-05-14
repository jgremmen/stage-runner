package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public interface StageContext<S extends Enum<S>> extends StageConfigurer<S>
{
  @Contract(pure = true)
  <T> T getData(@NotNull String name);


  @Contract(pure = true)
  @NotNull S getCurrentStage();


  @Contract(pure = true)
  @NotNull Set<S> getProcessedStages();


  @Contract(pure = true)
  @NotNull Set<S> getRemainingStages();


  void abort();


  @Contract(pure = true)
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean isAborted();




  enum State
  {
    IDLE,
    RUNNING,
    FINISHED,
    ABORTED;


    @Contract(pure = true)
    public boolean isTerminated() {
      return this == FINISHED || this == ABORTED;
    }
  }
}
