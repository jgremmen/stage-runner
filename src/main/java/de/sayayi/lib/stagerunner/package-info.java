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

/**
 * Public API of the stage runner library.
 * <p>
 * A stage runner executes {@link de.sayayi.lib.stagerunner.StageFunction stage functions} in the order of the
 * constants of a user defined stage enum. This package contains the interfaces used by applications to configure and
 * run stage runners:
 * <ul>
 *   <li>
 *     {@link de.sayayi.lib.stagerunner.StageRunnerFactory} creates stage runners sharing the same stage function
 *     configuration.
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.stagerunner.StageRunner} executes the configured stage functions.
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.stagerunner.StageFunction} is a unit of work executed within a specific stage.
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.stagerunner.StageFunctionConfigurer} adds regular and named stage functions.
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.stagerunner.StageContext} provides data and progress information to stage functions during
 *     an execution.
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.stagerunner.StageRunnerCallback} observes the execution and handles failing stage functions.
 *   </li>
 * </ul>
 * <p>
 * Ready to use implementations are provided by the {@link de.sayayi.lib.stagerunner.spi} package.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
package de.sayayi.lib.stagerunner;
