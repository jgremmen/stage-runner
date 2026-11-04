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
 * Spring integration for the stage runner library.
 * <p>
 * The {@link de.sayayi.lib.stagerunner.spring.StageRunnerFactoryProcessor} scans Spring managed beans for methods
 * carrying a stage function annotation and registers them with a {@link de.sayayi.lib.stagerunner.StageRunnerFactory}.
 * The registration process is customizable through several strategy interfaces:
 * <ul>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.StageFunctionFilter} decides which discovered stage functions are
 *       registered.</li>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.StageFunctionBuilder} turns an annotated method into an executable
 *       stage function.</li>
 *   <li>{@link de.sayayi.lib.stagerunner.spring.StageRunnerProxyBuilder} creates the proxy that implements the
 *       user defined stage runner interface. {@link de.sayayi.lib.stagerunner.spring.StageRunnerParameter} describes
 *       how each parameter of the interface method is passed to the stage runner.</li>
 * </ul>
 * The stage function annotation itself is described by
 * {@link de.sayayi.lib.stagerunner.spring.StageFunctionAnnotation} and is built from the meta annotations in
 * {@link de.sayayi.lib.stagerunner.spring.annotation}. Generated stage runner proxies implement
 * {@link de.sayayi.lib.stagerunner.spring.FactoryAccessor}, which gives access to the underlying stage runner
 * factory.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
package de.sayayi.lib.stagerunner.spring;
