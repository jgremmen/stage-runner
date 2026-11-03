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
import org.jetbrains.annotations.NotNull;


/**
 * Configuration interface for adding stage functions to a stage runner.
 * <p>
 * It is implemented by stage runner factories, where added functions apply to every subsequently created runner, and by
 * {@link StageContext}, where added functions only apply to the current execution.
 * <p>
 * Stage functions are executed in stage order. Within the same stage, functions with a lower order are executed first;
 * functions with identical stage and order are executed in the order they have been added.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0 (renamed from StageConfigurer)
 */
@FunctionalInterface
public interface StageFunctionConfigurer<S extends Enum<S>>
{
  /** Order used for a stage function if no specific order is supplied. */
  int DEFAULT_ORDER = 1000;


  /**
   * Adds a stage function to {@code stage} using the {@link #DEFAULT_ORDER default order} and no description.
   *
   * @param stage     stage, not {@code null}
   * @param function  stage function, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage function cannot be added
   *
   * @see #addStageFunction(Enum, int, String, StageFunction)
   */
  default void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    addStageFunction(stage, DEFAULT_ORDER, null, function);
  }


  /**
   * Adds a stage function with a description to {@code stage} using the {@link #DEFAULT_ORDER default order}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param stage        stage, not {@code null}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage function cannot be added
   *
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
   */
  default void addStageFunction(@NotNull S stage, String description, @NotNull StageFunction<S> function) {
    addStageFunction(stage, DEFAULT_ORDER, description, function);
  }


  /**
   * Adds a stage function to {@code stage} using the given {@code order} and no description.
   *
   * @param stage        stage, not {@code null}
   * @param order        function order within the given {@code stage}
   * @param function     stage function, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage function cannot be added
   */
  default void addStageFunction(@NotNull S stage, int order, @NotNull StageFunction<S> function) {
    addStageFunction(stage, order, null, function);
  }


  /**
   * Adds a stage function with a description to {@code stage} using the given {@code order}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param stage        stage, not {@code null}
   * @param order        function order within the given {@code stage}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the stage function cannot be added, for example because a stage
   *                                            runner execution has already terminated or passed beyond the given
   *                                            {@code stage} and {@code order}
   *
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
   */
  void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function);




  /**
   * Configuration interface for registering named stage functions.
   * <p>
   * Unlike regular stage functions, a named stage function is not executed by default. It must be enabled for a
   * specific execution, using {@link StageContext#enableNamedStageFunction(String)} or
   * {@link StageContext#enableNamedStageFunctions(java.util.function.Predicate)}.
   *
   * @param <S>  Stage enum type
   *
   * @author Jeroen Gremmen
   * @since 0.3.0
   */
  @FunctionalInterface
  interface Named<S extends Enum<S>>
  {
    /**
     * Registers a named stage function for {@code stage} using the
     * {@link StageFunctionConfigurer#DEFAULT_ORDER default order} and no description.
     *
     * @param name      stage function name, not empty or {@code null}
     * @param stage     stage, not {@code null}
     * @param function  stage function, not {@code null}
     *
     * @throws StageRunnerConfigurationException  if {@code name} is empty or not unique
     */
    default void namedStageFunction(@NotNull String name, @NotNull S stage, @NotNull StageFunction<S> function) {
      namedStageFunction(name, stage, DEFAULT_ORDER, null, function);
    }


    /**
     * Registers a named stage function with a description for {@code stage} using the
     * {@link StageFunctionConfigurer#DEFAULT_ORDER default order}.
     * <p>
     * The description is optional and will be passed to the
     * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
     * function when running through all stage functions.
     *
     * @param name         stage function name, not empty or {@code null}
     * @param stage        stage, not {@code null}
     * @param description  stage function description or {@code null} for no description
     * @param function     stage function, not {@code null}
     *
     * @throws StageRunnerConfigurationException  if {@code name} is empty or not unique
     */
    default void namedStageFunction(@NotNull String name, @NotNull S stage, String description,
                                    @NotNull StageFunction<S> function) {
      namedStageFunction(name, stage, DEFAULT_ORDER, description, function);
    }


    /**
     * Registers a named stage function for {@code stage} using the given {@code order} and no description.
     *
     * @param name      stage function name, not empty or {@code null}
     * @param stage     stage, not {@code null}
     * @param order     function order within the given {@code stage}
     * @param function  stage function, not {@code null}
     *
     * @throws StageRunnerConfigurationException  if {@code name} is empty or not unique
     */
    default void namedStageFunction(@NotNull String name, @NotNull S stage, int order,
                                    @NotNull StageFunction<S> function) {
      namedStageFunction(name, stage, order, null, function);
    }


    /**
     * Registers a named stage function with a description for {@code stage} using the given {@code order}.
     * <p>
     * The description is optional and will be passed to the
     * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
     * function when running through all stage functions.
     *
     * @param name         stage function name, not empty or {@code null}
     * @param stage        stage, not {@code null}
     * @param order        function order within the given {@code stage}
     * @param description  stage function description or {@code null} for no description
     * @param function     stage function, not {@code null}
     *
     * @throws StageRunnerConfigurationException  if {@code name} is empty or not unique
     *
     * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
     *
     * @since 0.3.0
     */
    void namedStageFunction(@NotNull String name, @NotNull S stage, int order, String description,
                            @NotNull StageFunction<S> function);
  }
}
