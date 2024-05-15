package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public interface StageRunnerCallback<S extends Enum<S>>
{
  default void preStageCallback(@NotNull StageContext<S> stageContext) {
  }


  default void preStageFunctionCallback(@NotNull StageContext<S> stageContext) {
  }


  default void postStageFunctionCallback(@NotNull StageContext<S> stageContext) {
  }


  default void postStageCallback(@NotNull StageContext<S> stageContext, @NotNull S stage) {
  }


  default void stageExceptionHandler(@NotNull StageContext<S> context,
                                     @NotNull Throwable exception) {
    context.abort();
  }
}
