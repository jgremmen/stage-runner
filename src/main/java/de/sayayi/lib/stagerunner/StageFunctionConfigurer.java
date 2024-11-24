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

import org.jetbrains.annotations.NotNull;


/**
 * The stage configurer provides methods to add new stage functions.
 *
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
@FunctionalInterface
public interface StageFunctionConfigurer<S extends Enum<S>>
{
  /** Default stage order if no specific order is supplied. */
  int DEFAULT_ORDER = 1000;


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the {@link #DEFAULT_ORDER}.
   *
   * @param stage     stage, not {@code null}
   * @param function  stage function, not {@code null}
   *
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
   */
  default void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    addStageFunction(stage, DEFAULT_ORDER, null, function);
  }


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the {@link #DEFAULT_ORDER}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param stage        stage, not {@code null}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   *
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
   */
  default void addStageFunction(@NotNull S stage, String description, @NotNull StageFunction<S> function) {
    addStageFunction(stage, DEFAULT_ORDER, description, function);
  }


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the given {@code order}.
   *
   * @param stage        stage, not {@code null}
   * @param order        function order within the given {@code stage}
   * @param function     stage function, not {@code null}
   */
  default void addStageFunction(@NotNull S stage, int order, @NotNull StageFunction<S> function) {
    addStageFunction(stage, order, null, function);
  }


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the given {@code order}.
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
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String) 
   */
  void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function);




  /**
   * The named stage configurer provides methods to add new stage functions, which can be enabled by name.
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
     * Add a named stage function to {@code stage}. The function will be inserted appropriately using the
     * {@link StageFunctionConfigurer#DEFAULT_ORDER}.
     *
     * @param name      stage function name, not empty or {@code null}
     * @param stage     stage, not {@code null}
     * @param function  stage function, not {@code null}
     */
    default void namedStageFunction(@NotNull String name, @NotNull S stage, @NotNull StageFunction<S> function) {
      namedStageFunction(name, stage, DEFAULT_ORDER, null, function);
    }


    /**
     * Add a named stage function to {@code stage}. The function will be inserted appropriately using the
     * {@link StageFunctionConfigurer#DEFAULT_ORDER}.
     * <p>
     * The description is optional and will be passed to the
     * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
     * function when running through all stage functions.
     *
     * @param name         stage function name, not empty or {@code null}
     * @param stage        stage, not {@code null}
     * @param description  stage function description or {@code null} for no description
     * @param function     stage function, not {@code null}
     */
    default void namedStageFunction(@NotNull String name, @NotNull S stage, String description,
                                    @NotNull StageFunction<S> function) {
      namedStageFunction(name, stage, DEFAULT_ORDER, description, function);
    }


    /**
     * Add a named stage function to {@code stage}. The function will be inserted appropriately using the given
     * {@code order}.
     *
     * @param name      stage function name, not empty or {@code null}
     * @param stage     stage, not {@code null}
     * @param order     function order within the given {@code stage}
     * @param function  stage function, not {@code null}
     */
    default void namedStageFunction(@NotNull String name, @NotNull S stage, int order,
                                    @NotNull StageFunction<S> function) {
      namedStageFunction(name, stage, order, null, function);
    }


    /**
     * Add a named stage function to {@code stage}. The function will be inserted appropriately using the given
     * {@code order}.
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
     * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
     */
    void namedStageFunction(@NotNull String name, @NotNull S stage, int order, String description,
                            @NotNull StageFunction<S> function);
  }
}
