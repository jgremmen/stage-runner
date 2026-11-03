/*
 * Copyright 2024 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.stagerunner;

import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.jetbrains.annotations.NotNull;


/**
 * Callback for observing and influencing a stage runner execution.
 * <p>
 * It provides notification methods which are invoked at the start and end of each stage and stage function, as well as
 * exception handlers which decide how failures of stage functions are handled. All methods have a default
 * implementation, so implementations only need to override the methods they are interested in.
 * <p>
 * For a single stage function, the callback methods are invoked in this order:
 * <ul>
 *   <li>
 *     {@link #preStageCallback(StageContext)}, if the function is the first one of its stage
 *   </li>
 *   <li>
 *     {@link #preStageFunctionCallback(StageContext, String)}
 *   </li>
 *   <li>
 *     {@link #stageExceptionHandler(StageContext, Throwable)} or
 *     {@link #stageConfigurationExceptionHandler(StageContext, StageRunnerConfigurationException)}, if the function has
 *     failed
 *   </li>
 *   <li>
 *     {@link #postStageFunctionCallback(StageContext)}
 *   </li>
 *   <li>
 *     {@link #postStageCallback(StageContext, Enum)}, if the function is the last one of its stage and the execution
 *     has neither been aborted nor terminated by an exception
 *   </li>
 * </ul>
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 */
public interface StageRunnerCallback<S extends Enum<S>>
{
  /**
   * Default callback instance which ignores all notifications and stops the stage runner execution by throwing a
   * {@link StageRunnerException} in case of an exception.
   */
  @SuppressWarnings("rawtypes")
  StageRunnerCallback<?> DEFAULT = new StageRunnerCallback() {};


  /**
   * Notifies the callback at the beginning of a new stage, before its first stage function is executed.
   *
   * @param stageContext  stage context, not {@code null}
   */
  default void preStageCallback(@NotNull StageContext<S> stageContext) {
  }


  /**
   * Notifies the callback before a stage function is executed.
   *
   * @param stageContext  stage context, not {@code null}
   * @param description   optional description for the stage function which was provided on
   *                      {@link StageFunctionConfigurer#addStageFunction(Enum, String, StageFunction) registration} or
   *                      {@code null}
   */
  default void preStageFunctionCallback(@NotNull StageContext<S> stageContext, String description) {
  }


  /**
   * Notifies the callback after a stage function has been executed, regardless of whether it has succeeded.
   * <p>
   * In case of an exception, {@link #stageExceptionHandler(StageContext, Throwable)} or
   * {@link #stageConfigurationExceptionHandler(StageContext, StageRunnerConfigurationException)} has already been
   * invoked.
   *
   * @param stageContext  stage context, not {@code null}
   */
  default void postStageFunctionCallback(@NotNull StageContext<S> stageContext) {
  }


  /**
   * Notifies the callback after all stage functions of a stage have been executed.
   * <p>
   * This method is not invoked for the current stage if the execution has been aborted.
   *
   * @param stageContext  stage context, not {@code null}
   * @param stage         stage which has completed, not {@code null}
   */
  default void postStageCallback(@NotNull StageContext<S> stageContext, @NotNull S stage) {
  }


  /**
   * Notifies the callback that a stage function has been added to the running execution, either through
   * {@link StageContext#addStageFunction(Enum, int, String, StageFunction)} or by enabling a named stage function.
   *
   * @param stage        current or future stage the function was added to, never {@code null}
   * @param order        order of the stage function within {@code stage}
   * @param description  optional description of the function or {@code null}
   *
   * @since 0.3.4
   */
  default void addStageFunctionCallback(@NotNull S stage, int order, String description) {
  }


  /**
   * Handles an exception thrown by the stage function currently being processed.
   * <p>
   * This handler is not invoked for a {@link StageRunnerConfigurationException}; those are passed to
   * {@link #stageConfigurationExceptionHandler(StageContext, StageRunnerConfigurationException)} instead. It is not
   * invoked for an {@link Error} either; errors terminate the execution and are rethrown unchanged. If the handler
   * returns normally, the exception is considered handled and the execution continues with the next stage function.
   * <p>
   * The default implementation rethrows a {@link StageRunnerException} as is and wraps any other exception inside a
   * {@link StageRunnerException}.
   * <p>
   * Any exception thrown by this handler will lead to exiting the stage runner. On its way out, callback
   * {@link #postStageFunctionCallback(StageContext)} will be invoked.
   *
   * @param stageContext  stage context, not {@code null}
   * @param exception     exception thrown by the current stage function, not {@code null}
   *
   * @throws StageRunnerException  if the exception is not handled (default behavior)
   */
  default void stageExceptionHandler(@NotNull StageContext<S> stageContext, @NotNull Throwable exception)
  {
    if (exception instanceof StageRunnerException)
      throw (StageRunnerException)exception;
    else
      throw new StageRunnerException(exception.getMessage(), exception);
  }


  /**
   * Handles a configuration exception thrown by the stage function currently being processed, for example when the
   * function adds another stage function at a position the execution has already passed.
   * <p>
   * If the handler returns normally, the exception is considered handled and the execution continues with the next
   * stage function, unless the execution has been aborted.
   * <p>
   * The default implementation rethrows the exception, which leads to exiting the stage runner. On its way out,
   * callback {@link #postStageFunctionCallback(StageContext)} will be invoked.
   *
   * @param stageContext            stage context, not {@code null}
   * @param configurationException  configuration exception thrown by the current stage function, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the exception is not handled (default behavior)
   *
   * @since 0.4.0
   */
  default void stageConfigurationExceptionHandler(@SuppressWarnings("unused") @NotNull StageContext<S> stageContext,
                                                  @NotNull StageRunnerConfigurationException configurationException) {
    throw configurationException;
  }
}
