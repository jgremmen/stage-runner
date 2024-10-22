package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;


/**
 * Interface providing callback functions and exception handler which are invoked during stage running.
 *
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public interface StageRunnerCallback<S extends Enum<S>>
{
  /**
   * Default callback instance aborting stage runner execution in case of an exception.
   */
  @SuppressWarnings("rawtypes")
  StageRunnerCallback<?> INSTANCE = new StageRunnerCallback() {};


  /**
   * Callback method which is invoked at the beginning of a new stage.
   *
   * @param stageContext  stage context, not {@code null}
   */
  default void preStageCallback(@NotNull StageContext<S> stageContext) {
  }


  /**
   * Callback method which is invoked at the beginning of a stage function.
   *
   * @param stageContext  stage context, not {@code null}
   * @param description   optional description for the stage function which was provided on
   *                      {@link StageConfigurer#addStageFunction(Enum, String, StageFunction) registration}
   */
  default void preStageFunctionCallback(@NotNull StageContext<S> stageContext, String description) {
  }


  /**
   * Callback method which is invoked after each stage function.
   * <p>
   * In case of an exception, {@link #stageExceptionHandler(StageContext, Throwable)} has already been invoked.
   *
   * @param stageContext  stage context, not {@code null}
   */
  default void postStageFunctionCallback(@NotNull StageContext<S> stageContext) {
  }


  /**
   * Callback method which is invoked after each stage has completed.
   *
   * @param stageContext  stage context, not {@code null}
   * @param stage         stage which has completed, not {@code null}
   */
  default void postStageCallback(@NotNull StageContext<S> stageContext, @NotNull S stage) {
  }


  /**
   * Exception handler which is invoked if the stage function currently being processed throws an exception.
   * <p>
   * The default implementation does not report the exception but only aborts the stage runner.
   * <p>
   * Any exception thrown by this handler will lead to exiting the stage runner. On its way out, callback
   * {@link #postStageFunctionCallback(StageContext)} will be invoked.
   *
   * @param stageContext  stage context, not {@code null}
   * @param exception     exception thrown by the current stage function, not {@code null}
   */
  default void stageExceptionHandler(@NotNull StageContext<S> stageContext, @NotNull Throwable exception) {
    stageContext.abort();
  }
}
