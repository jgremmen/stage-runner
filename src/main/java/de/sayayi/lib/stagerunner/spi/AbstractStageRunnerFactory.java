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
package de.sayayi.lib.stagerunner.spi;

import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageFunctionConfigurer;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;


/**
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 */
public abstract class AbstractStageRunnerFactory<S extends Enum<S>>
    implements StageRunnerFactory<S>, StageFunctionConfigurer<S>, StageFunctionConfigurer.Named<S>
{
  protected final Class<S> stageEnumType;

  final StageOrderFunctionArray<S> functionArray;
  final Map<String,StageOrderFunction<S>> namedStageFunctions;


  protected AbstractStageRunnerFactory(@NotNull Class<S> stageEnumType)
  {
    this.stageEnumType = stageEnumType;

    functionArray = new StageOrderFunctionArray<>();
    namedStageFunctions = new HashMap<>();
  }


  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function) {
    functionArray.add(new StageOrderFunction<>(stage, description, order, function));
  }


  @Override
  public void namedStageFunction(@NotNull String name, @NotNull S stage, int order, String description,
                                 @NotNull StageFunction<S> function)
  {
    if (requireNonNull(name, "name must not be null").isEmpty() )
      throw new StageRunnerConfigurationException("name must not be empty");

    if (namedStageFunctions.containsKey(name))
      throw new StageRunnerConfigurationException("name '" + name + "' must be unique for this stage runner factory");

    namedStageFunctions.put(name, new StageOrderFunction<>(stage, description, order, function));
  }
}
