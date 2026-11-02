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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Factory for creating stage runner instances.
 * <p>
 * The factory holds the stage function configuration that is shared by all runners it creates.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 *
 * @see de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory
 */
@FunctionalInterface
public interface StageRunnerFactory<S extends Enum<S>>
{
  /**
   * Creates a new stage runner which executes the stage functions configured for this factory.
   *
   * @return  newly created stage runner, never {@code null}
   */
  @Contract(value = "-> new", pure = true)
  @NotNull StageRunner<S> createRunner();
}
