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
 * Stage runner library for executing an ordered sequence of stage functions.
 * <p>
 * A stage runner processes a user defined enum whose constants represent the stages of a workflow. For each stage
 * one or more {@link de.sayayi.lib.stagerunner.StageFunction stage functions} can be registered. When the runner
 * is executed, the functions are invoked in stage order, allowing callers to inspect progress and share data
 * through the {@link de.sayayi.lib.stagerunner.StageContext stage context}.
 * <p>
 * Runner instances are obtained from a {@link de.sayayi.lib.stagerunner.StageRunnerFactory} and are intended to be
 * used once. A {@link de.sayayi.lib.stagerunner.StageRunnerCallback} may be supplied to observe or influence the
 * execution flow. The {@code spi} package offers reusable base implementations that simplify building a custom
 * factory.
 * <p>
 * The {@code spring} package provides optional integration with the Spring Framework. It discovers annotated
 * methods on Spring managed beans and wires them into a stage runner factory, so applications can declare stage
 * functions using the annotations in the {@code spring.annotation} package instead of registering them manually.
 *
 * @author Jeroen Gremmen
 */
module de.sayayi.lib.stagerunner
{
  // optional requirement for Spring
  requires static net.bytebuddy;
  requires static spring.aop;
  requires static spring.core;
  requires static spring.beans;
  requires static spring.jcl;

  // compile time requirement
  requires static org.jetbrains.annotations;

  // exports
  exports de.sayayi.lib.stagerunner;
  exports de.sayayi.lib.stagerunner.exception;
  exports de.sayayi.lib.stagerunner.spi;
  exports de.sayayi.lib.stagerunner.spring;
  exports de.sayayi.lib.stagerunner.spring.annotation;

  // provide access
  opens de.sayayi.lib.stagerunner.spring to spring.core;
  opens de.sayayi.lib.stagerunner.spring.builder to net.bytebuddy;
}
