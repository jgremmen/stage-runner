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
import org.jetbrains.annotations.NotNull;


/**
 * Ready to use stage runner factory that produces runners backed by the default {@link AbstractStageRunner}
 * implementation.
 * <p>
 * This factory is sufficient for most use cases. Applications that need custom runner behavior can subclass
 * {@link AbstractStageRunnerFactory} directly and provide their own {@link #createRunner()} implementation.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 */
public class DefaultStageRunnerFactory<S extends Enum<S>> extends AbstractStageRunnerFactory<S>
{
  /**
   * Create a new default stage runner factory for the given stage enum type.
   *
   * @param stageEnumType  stage enum class, not {@code null}
   */
  public DefaultStageRunnerFactory(@NotNull Class<S> stageEnumType) {
    super(stageEnumType);
  }


  @Override
  public @NotNull StageRunner<S> createRunner() {
    return new AbstractStageRunner<>(DefaultStageRunnerFactory.this) {};
  }
}
