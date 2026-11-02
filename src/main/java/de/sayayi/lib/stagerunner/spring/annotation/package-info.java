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
 * Annotations for declaring stage functions and their data parameters in the Spring integration.
 * <ul>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.annotation.StageDefinition} contains the meta annotations used to
 *       build an application specific stage function annotation.</li>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.annotation.Data} binds a method parameter to an entry of the stage
 *       runner data.</li>
 * </ul>
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 *
 * @see de.sayayi.lib.stagerunner.spring.StageRunnerFactoryProcessor
 */
package de.sayayi.lib.stagerunner.spring.annotation;
