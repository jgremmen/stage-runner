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
package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageFunctionConfigurer;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Stage runner proxies may implement this interface to provide access to the internal stage runner factory.
 *
 * @author Jeroen Gremmen
 * @since 0.3.2
 */
@FunctionalInterface
public interface FactoryAccessor<S extends Enum<S>>
{
  /**
   * Return the populated stage runner factory.
   *
   * @return  stage runner factory, never {@code null}
   */
  @Contract(pure = true)
  @NotNull StageRunnerFactory<S> getStageRunnerFactory();


  /**
   * Convenience method, which allows access to the stage function configurer. If the internal stage runner factory
   * is not configurable, an {@link IllegalStateException} is thrown.
   *
   * @return  stage function configurer, never {@code null}
   */
  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  default @NotNull StageFunctionConfigurer<S> getStageFunctionConfigurer()
  {
    var factory = getStageRunnerFactory();
    if (factory instanceof StageFunctionConfigurer)
      return (StageFunctionConfigurer<S>)factory;

    throw new IllegalStateException("stage runner factory is not configurable");
  }


  /**
   * Convenience method, which allows access to the named stage function configurer. If the internal stage runner
   * factory is not configurable, an {@link IllegalStateException} is thrown.
   *
   * @return  stage function configurer, never {@code null}
   */
  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  default @NotNull StageFunctionConfigurer.Named<S> getNamedStageFunctionConfigurer()
  {
    var factory = getStageRunnerFactory();
    if (factory instanceof StageFunctionConfigurer.Named)
      return (StageFunctionConfigurer.Named<S>)factory;

    throw new IllegalStateException("stage runner factory is not configurable");
  }
}
