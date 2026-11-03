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

import static java.util.Objects.requireNonNull;


/**
 * Internal descriptor bundling a stage function with its stage, ordering value and optional description.
 * <p>
 * Instances are immutable and are used as the element type of {@link StageOrderFunctionArray}.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@SuppressWarnings("ClassCanBeRecord")
final class StageOrderFunction<S extends Enum<S>>
{
  /** Stage the function belongs to, never {@code null}. */
  final @NotNull S stage;

  /** Optional stage function description, may be {@code null}. */
  final String description;

  /** Ordering value of the function within its stage. */
  final int order;

  /** Stage function to execute, never {@code null}. */
  final @NotNull StageFunction<S> function;


  /**
   * Creates a new stage function descriptor.
   *
   * @param stage        stage the function belongs to, not {@code null}
   * @param description  optional stage function description, may be {@code null}
   * @param order        ordering value for the function within its stage
   * @param function     stage function to execute, not {@code null}
   */
  StageOrderFunction(@NotNull S stage, String description, int order, @NotNull StageFunction<S> function)
  {
    this.stage = requireNonNull(stage, "stage must not be null");
    this.description = description;
    this.order = order;
    this.function = requireNonNull(function, "function must not be null");
  }


  /**
   * {@inheritDoc}
   */
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
