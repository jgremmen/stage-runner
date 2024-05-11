package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 * @param <D>  Data type
 */
public interface StageContext<S extends Enum<S>,D> extends StageConfigurer<S,D>
{
  @Contract(pure = true)
  D getData();


  @Contract(pure = true)
  @NotNull State getState();


  @Contract(pure = true)
  @NotNull S getCurrentStage();


  @Contract(pure = true)
  @NotNull Set<S> getProcessedStages();


  @Contract(pure = true)
  @NotNull Set<S> getRemainingStages();


  void abort();


  @Contract(pure = true)
  boolean isAborted();


  default void stageExceptionHandler(@NotNull StageContext<S,D> context,
                                     @NotNull Throwable exception) {
    abort();
  }




  enum State
  {
    IDLE,
    RUNNING,
    FINISHED
  }
}
