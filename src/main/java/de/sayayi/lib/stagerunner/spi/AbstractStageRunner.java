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
package de.sayayi.lib.stagerunner.spi;

import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Objects.requireNonNull;


/**
 * Abstract base implementation of {@link StageRunner}.
 * <p>
 * Each invocation of {@link #run(Map, StageRunnerCallback)} creates a fresh stage context that iterates through the
 * stage functions configured on the associated {@link AbstractStageRunnerFactory factory}. Subclasses may extend this
 * class in order to expose an application specific runner API on top of the standard runner contract.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public abstract class AbstractStageRunner<S extends Enum<S>> implements StageRunner<S>
{
  /** Factory providing the shared stage function configuration. */
  private final AbstractStageRunnerFactory<S> stageRunnerFactory;


  /**
   * Creates a new stage runner bound to the given factory.
   *
   * @param stageRunnerFactory  factory providing the shared stage function configuration, not {@code null}
   */
  protected AbstractStageRunner(@NotNull AbstractStageRunnerFactory<S> stageRunnerFactory) {
    this.stageRunnerFactory = requireNonNull(stageRunnerFactory, "stageRunnerFactory must not be null");
  }


  /**
   * {@inheritDoc}
   * <p>
   * Each invocation executes the stage functions configured on the factory at the time of invocation, using a new
   * stage context.
   */
  @Override
  public boolean run(@NotNull Map<String,Object> data, @NotNull StageRunnerCallback<S> callback)
  {
    requireNonNull(data, "data must not be null");
    requireNonNull(callback, "callback must not be null");

    return new StageContextImpl<>(stageRunnerFactory, data).run(callback);
  }
}
