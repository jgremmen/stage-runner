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

import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static de.sayayi.lib.stagerunner.StageRunnerCallback.defaultCallback;


/**
 * Executes the stage functions of a stage runner factory in stage order.
 * <p>
 * A new instance can be {@link StageRunnerFactory#createRunner() created} by the stage runner factory. The runners
 * provided by this library can be run any number of times; each run uses its own {@link StageContext stage context}.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageRunner<S extends Enum<S>>
{
  /**
   * Runs all stage functions using the {@link StageRunnerCallback#defaultCallback() default callback}.
   * <p>
   * The default callback stops the execution on the first exception thrown by a stage function.
   *
   * @param data  data, which can be accessed in stage functions, not {@code null}
   *
   * @return  {@code true} if the stage runner ran successfully, {@code false} if it has been
   *          {@link StageContext#abort() aborted}
   *
   * @throws StageRunnerException  if a stage function has failed
   * @throws Error                 any error thrown by a stage function, rethrown unchanged
   */
  default boolean run(@NotNull Map<String,Object> data) {
    return run(data, defaultCallback());
  }


  /**
   * Runs all stage functions, notifying {@code callback} about the progress of the execution.
   *
   * @param data      data, which can be accessed in stage functions, not {@code null}
   * @param callback  stage runner callback instance, not {@code null}
   *
   * @return  {@code true} if the stage runner ran successfully, {@code false} if it has been
   *          {@link StageContext#abort() aborted}
   *
   * @throws RuntimeException  any exception thrown by one of the {@code callback} methods, such as the
   *                           {@link StageRunnerException StageRunnerException}
   *                           thrown by the default exception handler
   * @throws Error             any error thrown by a stage function, rethrown unchanged
   */
  boolean run(@NotNull Map<String,Object> data, @NotNull StageRunnerCallback<S> callback);
}
