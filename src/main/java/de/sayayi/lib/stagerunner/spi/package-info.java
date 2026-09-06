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
 * Service provider interface for the stage runner library.
 * <p>
 * This package contains the base implementations that library users can build upon in order to create and
 * execute their own stage runners:
 * <ul>
 *   <li>{@link de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory} is a ready to use factory that fits
 *       most use cases.</li>
 *   <li>{@link de.sayayi.lib.stagerunner.spi.AbstractStageRunnerFactory} is the extension point for
 *       applications that need custom factory behaviour, such as producing a specific runner subtype.</li>
 *   <li>{@link de.sayayi.lib.stagerunner.spi.AbstractStageRunner} is the default runner implementation and
 *       can be subclassed to enrich the runner with additional API.</li>
 * </ul>
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
package de.sayayi.lib.stagerunner.spi;
