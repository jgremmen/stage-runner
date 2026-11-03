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
 * Unit of work executed by a stage runner as part of a specific stage.
 * <p>
 * Stage functions are registered with a {@link StageFunctionConfigurer}, either on the factory (applying to every
 * execution) or on the {@link StageContext} (applying to the current execution only).
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageFunction<S extends Enum<S>>
{
  /**
   * Executes this stage function.
   * <p>
   * Any exception thrown by this method is passed to the exception handlers of the {@link StageRunnerCallback} used for
   * the current execution. An {@link Error} is not passed to the exception handlers; it terminates the execution and is
   * rethrown unchanged.
   *
   * @param stageContext  stage runner context, never {@code null}
   */
  void process(@NotNull StageContext<S> stageContext);
}
