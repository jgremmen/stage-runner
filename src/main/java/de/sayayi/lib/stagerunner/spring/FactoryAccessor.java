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
 * Provides access to the stage runner factory behind a stage runner proxy.
 * <p>
 * Proxies created by the default {@link StageRunnerProxyBuilder} implement this interface. Casting a proxy to
 * {@code FactoryAccessor} allows applications to register additional stage functions programmatically.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.2
 */
@FunctionalInterface
public interface FactoryAccessor<S extends Enum<S>>
{
  /**
   * Returns the stage runner factory used by the proxy, populated with the discovered stage functions.
   *
   * @return  stage runner factory, never {@code null}
   */
  @Contract(pure = true)
  @NotNull StageRunnerFactory<S> getStageRunnerFactory();


  /**
   * Returns the stage runner factory as a stage function configurer.
   *
   * @return  stage function configurer, never {@code null}
   *
   * @throws IllegalStateException  if the stage runner factory does not implement {@link StageFunctionConfigurer}
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
   * Returns the stage runner factory as a named stage function configurer.
   *
   * @return  named stage function configurer, never {@code null}
   *
   * @throws IllegalStateException  if the stage runner factory does not implement {@link StageFunctionConfigurer.Named}
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
