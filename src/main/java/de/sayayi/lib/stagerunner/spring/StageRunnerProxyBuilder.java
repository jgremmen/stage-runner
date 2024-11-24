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

import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageRunnerProxyBuilder
{
  /**
   * Create and initialize a proxy stage runner. The generated proxy must implement {@code stageRunnerInterfaceType}
   * and use {@code stageRunnerFactory} for creating new staqe runner instances.
   * <p>
   * The {@code dataNames} array contains the data map key name for the corresponding
   * {@code stageRunnerInterfaceMethod} parameter. If the value in {@code dataNames} is {@code null}, it is not
   * required to store its parameter value in the data map.
   *
   * @param stageType                   stage enumeration type, not {@code null}
   * @param stageRunnerInterfaceType    stage runner interface type, not {@code null}
   * @param stageRunnerInterfaceMethod  functional interface method of {@code stageRunnerInterfaceType}, not {@code null}
   * @param dataNames                   data names array, not {@code null}
   * @param stageRunnerFactory          stage runner factory instance, not {@code null}
   *
   * @return  stage runner proxy instance, never {@code null}
   *
   * @param <R>  stage runner interface type, not {@code null}
   * @param <S>  stage enumeration type, not {@code null}
   */
  @Contract(pure = true)
  <R,S extends Enum<S>> @NotNull R createProxy(@NotNull Class<S> stageType,
                                               @NotNull Class<R> stageRunnerInterfaceType,
                                               @NotNull Method stageRunnerInterfaceMethod,
                                               @NotNull String[] dataNames,
                                               @NotNull StageRunnerFactory<S> stageRunnerFactory);
}
