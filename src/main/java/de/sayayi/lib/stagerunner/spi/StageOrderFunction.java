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
import org.jetbrains.annotations.NotNull;

import static de.sayayi.lib.stagerunner.StageFunctionConfigurer.DEFAULT_ORDER;


/**
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 */
final class StageOrderFunction<S extends Enum<S>>
{
  final @NotNull S stage;
  final String description;
  final int order;
  final @NotNull StageFunction<S> function;


  StageOrderFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    this(stage, null, DEFAULT_ORDER, function);
  }


  StageOrderFunction(@NotNull S stage, String description, int order,
                     @NotNull StageFunction<S> function)
  {
    this.stage = stage;
    this.description = description;
    this.order = order;
    this.function = function;
  }


  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (!(o instanceof StageOrderFunction))
      return false;

    var that = (StageOrderFunction<?>) o;

    return order == that.order && stage == that.stage && function.equals(that.function);
  }


  @Override
  public int hashCode() {
    return (stage.hashCode() * 31 + order) * 31 + function.hashCode();
  }


  @Override
  public String toString()
  {
    var s = new StringBuilder(getClass().getSimpleName())
        .append("(stage=").append(stage).append('@').append(order);

    if (description != null && !description.isEmpty())
      s.append(",description=").append(description);

    return s.append(')').toString();
  }
}
