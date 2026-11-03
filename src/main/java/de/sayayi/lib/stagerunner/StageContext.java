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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


/**
 * Context of a single stage runner execution, passed to every {@link StageFunction} and {@link StageRunnerCallback}
 * invocation.
 * <p>
 * The stage context provides:
 * <ul>
 *   <li>the data associated with this stage runner execution</li>
 *   <li>the current stage</li>
 *   <li>the processed and remaining stages</li>
 *   <li>methods for adding stage functions to the current or a future stage</li>
 *   <li>methods for enabling predefined named stage functions</li>
 *   <li>a function for {@link #abort() aborting} the execution</li>
 * </ul>
 * <p>
 * Stage functions added through this context (see {@link StageFunctionConfigurer}) only apply to the current execution.
 * A function must be positioned after the function currently being executed; otherwise the function is not added, the
 * execution is aborted and a {@link StageRunnerConfigurationException StageRunnerConfigurationException} is thrown.
 * Adding functions after the execution has terminated also results in this exception.
 * <p>
 * A stage context is bound to the thread executing the stage runner and is not thread safe.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public interface StageContext<S extends Enum<S>> extends StageFunctionConfigurer<S>
{
  /**
   * Returns the data value associated with {@code name}, as passed to {@link StageRunner#run(Map)}.
   * <p>
   * The value is cast to the type expected by the caller without any conversion. A mismatching type results in a
   * {@link ClassCastException} at the call site.
   *
   * @param name  data name, not {@code null}
   *
   * @return  data value for name {@code name} or {@code null} if no data is available
   *
   * @param <T>  data type
   *
   * @see StageRunner#run(Map)
   */
  @Contract(pure = true)
  <T> T getData(@NotNull String name);


  /**
   * Returns the stage currently being processed.
   * <p>
   * Within {@link StageRunnerCallback#postStageCallback(StageContext, Enum) postStageCallback}, this is the stage which
   * has just completed.
   *
   * @return  stage currently being processed, never {@code null}
   *
   * @throws StageRunnerException  if the stage runner has not started yet or has terminated
   */
  @Contract(pure = true)
  @NotNull S getCurrentStage();


  /**
   * Returns the stages which have been processed completely. The current stage is only part of this set within
   * {@link StageRunnerCallback#postStageCallback(StageContext, Enum) postStageCallback}, after it has completed.
   * <p>
   * The returned set is a snapshot; changes to it do not affect this context.
   *
   * @return  set with processed stages, never {@code null}
   */
  @Contract(pure = true)
  @NotNull Set<S> getProcessedStages();


  /**
   * Returns the stages which have at least one stage function waiting for execution. The current stage is never part of
   * this set.
   * <p>
   * The returned set is a snapshot; changes to it do not affect this context. After the stage runner has terminated,
   * the set is empty.
   *
   * @return  set with remaining stages, never {@code null}
   */
  @Contract(pure = true)
  @NotNull Set<S> getRemainingStages();


  /**
   * Aborts the stage runner execution.
   * <p>
   * The stage function currently being executed is not interrupted, but no further stage functions are executed after
   * it returns. If invoked from {@link StageRunnerCallback#preStageCallback(StageContext) preStageCallback} or
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback}, the stage
   * function about to be executed is not executed either. The stage runner then reports an unsuccessful run.
   *
   * @see #isAborted()
   */
  void abort();


  /**
   * Tells whether the stage runner execution has been aborted.
   *
   * @return  {@code true} if the stage runner has been aborted due to an invocation of {@link #abort()},
   *          {@code false} otherwise
   *
   * @see #abort()
   */
  @Contract(pure = true)
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean isAborted();


  /**
   * Enables the named stage function with the exact {@code name} for this execution.
   * <p>
   * Named stage functions are registered with the factory through {@link StageFunctionConfigurer.Named} and are only
   * executed if they are enabled. Enabling a function which has already been enabled has no effect.
   *
   * @param name  name of the stage function to enable, not {@code null}
   *
   * @return  {@code true} if a stage function with {@code name} exists and has been enabled by this invocation,
   *          {@code false} otherwise
   *
   * @throws StageRunnerConfigurationException  if the stage runner has terminated or has already passed beyond the
   *                                            stage and order of the stage function
   *
   * @since 0.3.0
   *
   * @see #enableNamedStageFunctions(Predicate)
   */
  default boolean enableNamedStageFunction(@NotNull String name) {
    return !enableNamedStageFunctions(n -> n.equals(name)).isEmpty();
  }


  /**
   * Enables all named stage functions whose name is accepted by {@code nameFilter} for this execution.
   * <p>
   * Stage functions which have already been enabled are not passed to the filter again.
   *
   * @param nameFilter  name filter predicate, not {@code null}. A stage function is enabled if and only if the
   *                    predicate ({@link Predicate#test(Object)}) returns {@code true} for its name
   *
   * @return  a set with the stage function names which have been enabled as a result of this invocation, never
   *          {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage runner has terminated or has already passed beyond the
   *                                            stage and order of an accepted stage function. In the latter case none
   *                                            of the accepted stage functions is enabled
   *
   * @since 0.3.0
   */
  @NotNull Set<String> enableNamedStageFunctions(@NotNull Predicate<String> nameFilter);


  /**
   * Returns all stage functions of this execution in execution order, including processed, failed and waiting ones.
   * Each function provides the {@link FunctionState state} it is in.
   * <p>
   * The returned list is an unmodifiable snapshot taken at the time of invocation.
   *
   * @return  list of all stage functions, never {@code null}
   *
   * @since 0.3.0
   */
  @Contract(pure = true)
  @NotNull @Unmodifiable List<Function<S>> getFunctions();




  /**
   * Read only view of a stage function within a stage runner execution.
   *
   * @param <S>  stage enumeration type
   *
   * @see StageContext#getFunctions()
   *
   * @since 0.3.0
   */
  interface Function<S extends Enum<S>>
  {
    /**
     * Returns the execution state of this function.
     *
     * @return  function state, never {@code null}
     */
    @Contract(pure = true)
    @NotNull FunctionState getFunctionState();


    /**
     * Returns the stage this function belongs to.
     *
     * @return  stage enumeration, never {@code null}
     */
    @Contract(pure = true)
    @NotNull S getStage();


    /**
     * Returns the order of this function within its stage. Functions with a lower order are executed first.
     *
     * @return  function order
     *
     * @see StageFunctionConfigurer#DEFAULT_ORDER
     */
    @Contract(pure = true)
    int getOrder();


    /**
     * Returns the description provided when this function was registered.
     *
     * @return  function description or {@code null} if no description was provided
     */
    @Contract(pure = true)
    String getDescription();
  }




  /**
   * Execution state of a stage function.
   *
   * @since 0.3.0
   *
   * @see Function#getFunctionState()
   * @see StageContext#getFunctions()
   */
  enum FunctionState
  {
    /** Function has been executed successfully. */
    PROCESSED,

    /** Function is currently executing. */
    EXECUTING,

    /** Function execution has failed with an exception. */
    FAILED,

    /** Function is waiting for execution. */
    WAITING
  }
}
