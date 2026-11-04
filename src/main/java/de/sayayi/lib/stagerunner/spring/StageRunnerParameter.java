/*
 * Copyright 2026 Jeroen Gremmen
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
package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageRunnerCallback;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;


/**
 * Describes how a parameter of the stage runner interface method is passed to the stage runner: either as an entry of
 * the data map ({@link DataParameter}) or as the callback for the execution ({@link CallbackParameter}).
 *
 * @see StageRunnerProxyBuilder#createProxy(Class, Class, java.lang.reflect.Method, java.util.List,
 *      de.sayayi.lib.stagerunner.StageRunnerFactory)
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
public sealed interface StageRunnerParameter
    permits StageRunnerParameter.DataParameter, StageRunnerParameter.CallbackParameter
{
  /**
   * Parameter whose value is stored in the data map under {@code name}.
   *
   * @param name  data name, not {@code null} or empty
   */
  record DataParameter(@NotNull String name) implements StageRunnerParameter
  {
    /**
     * Creates a data parameter.
     *
     * @param name  data name, not {@code null} or empty
     *
     * @throws NullPointerException      if {@code name} is {@code null}
     * @throws IllegalArgumentException  if {@code name} is empty
     */
    public DataParameter
    {
      if (requireNonNull(name, "name must not be null").isEmpty())
        throw new IllegalArgumentException("name must not be empty");
    }
  }




  /**
   * The {@link StageRunnerCallback} parameter, which is not stored in the data map but used as callback for the
   * execution.
   */
  record CallbackParameter() implements StageRunnerParameter {
  }
}
