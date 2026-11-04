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


/**
 * Holder for the default callback instance, which ignores all notifications and stops the stage runner execution by
 * throwing a {@link de.sayayi.lib.stagerunner.exception.StageRunnerException StageRunnerException} in case of an
 * exception.
 * <p>
 * The instance is not part of the public API; use {@link StageRunnerCallback#defaultCallback()} instead.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
final class DefaultStageRunnerCallback
{
  /**
   * Shared default callback instance. It only relies on the default methods of {@link StageRunnerCallback} and is
   * therefore stateless, thread safe and usable for any stage type.
   */
  @SuppressWarnings("rawtypes")
  static final StageRunnerCallback<?> INSTANCE = new StageRunnerCallback() {};


  /**
   * Prevents instantiation; this class only holds the {@link #INSTANCE default callback instance}.
   */
  private DefaultStageRunnerCallback() {
  }
}
