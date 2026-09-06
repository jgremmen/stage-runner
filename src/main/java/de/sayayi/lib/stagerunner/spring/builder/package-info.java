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
 * Default builder implementations for the Spring integration of the stage runner library.
 * <p>
 * This package provides runtime code generation based implementations for the strategy interfaces defined in
 * {@link de.sayayi.lib.stagerunner.spring}:
 * <ul>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.builder.StageFunctionBuilderImpl} turns a Spring managed bean method
 *       annotated with a stage function annotation into an executable
 *       {@link de.sayayi.lib.stagerunner.StageFunction}.</li>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.builder.StageRunnerProxyBuilderImpl} generates a proxy that implements
 *       a user defined stage runner interface and delegates to a
 *       {@link de.sayayi.lib.stagerunner.StageRunnerFactory}.</li>
 * </ul>
 * Both builders share common helpers through {@link de.sayayi.lib.stagerunner.spring.builder.AbstractBuilder}.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
package de.sayayi.lib.stagerunner.spring.builder;
