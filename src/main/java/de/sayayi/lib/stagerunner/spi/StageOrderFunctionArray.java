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

import de.sayayi.lib.stagerunner.StageContext.FunctionState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static de.sayayi.lib.stagerunner.StageContext.FunctionState.WAITING;
import static java.lang.Math.max;
import static java.lang.System.arraycopy;
import static java.lang.reflect.Array.newInstance;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.fill;


/**
 * Internal ordered array of {@link StageOrderFunction stage functions}.
 * <p>
 * Entries are kept sorted by stage and, within each stage, by order value. New entries are inserted at the position
 * that keeps this ordering while preserving insertion order between functions sharing the same stage and order.
 * Alongside each function the array also tracks its current
 * {@link de.sayayi.lib.stagerunner.StageContext.FunctionState execution state} so that a stage runner can report
 * progress.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
final class StageOrderFunctionArray<S extends Enum<S>>
{
  /** Backing array of stage functions, or {@code null} if no function has been added yet. */
  StageOrderFunction<S>[] functions;

  /** Execution state for each entry in {@link #functions}, or {@code null} if no function has been added yet. */
  FunctionState[] executionState;

  /** Number of stage functions in this array. */
  int size;


  /**
   * Creates a new empty stage order function array.
   */
  StageOrderFunctionArray()
  {
    functions = null;
    executionState = null;
    size = 0;
  }


  /**
   * Creates a copy of the given stage order function array. The copy shares the {@link StageOrderFunction} instances
   * with the source but starts with all execution states reset to {@link FunctionState#WAITING WAITING}.
   *
   * @param array  source array, not {@code null}
   */
  StageOrderFunctionArray(@NotNull StageOrderFunctionArray<S> array)
  {
    if (array.functions == null)
    {
      functions = null;
      executionState = null;
      size = 0;
    }
    else
    {
      functions = copyOf(array.functions, size = array.size);
      fill(executionState = new FunctionState[size], WAITING);
    }
  }


  /**
   * Inserts the given stage function at the position that keeps the array sorted by stage and order.
   * <p>
   * Functions sharing the same stage and order are appended after previously inserted ones so insertion order is
   * retained. The backing array grows on demand.
   *
   * @param function  stage function to add, not {@code null}
   *
   * @return  the index at which the function has been inserted
   *
   * @see #insertionIndex(StageOrderFunction)
   */
  int add(@NotNull StageOrderFunction<S> function) {
    return insert(insertionIndex(function), function);
  }


  /**
   * Returns the position at which the given stage function would be inserted by {@link #add(StageOrderFunction)},
   * without modifying this array.
   *
   * @param function  stage function, not {@code null}
   *
   * @return  insertion index, in the range {@code 0} to {@link #size}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  int insertionIndex(@NotNull StageOrderFunction<S> function)
  {
    var low = 0;

    for(var high = size - 1; low <= high;)
    {
      final var mid = (low + high) >>> 1;

      if (compare(function, functions[mid]) > 0)
        low = mid + 1;
      else
        high = mid - 1;
    }

    return low;
  }


  /**
   * Inserts the given stage function at {@code index}, which must have been obtained from
   * {@link #insertionIndex(StageOrderFunction)} for the current state of this array. The backing array grows on demand,
   * by a number of entries which decreases as the array grows.
   *
   * @param index     insertion index, as returned by {@link #insertionIndex(StageOrderFunction)}
   * @param function  stage function to insert, not {@code null}
   *
   * @return  {@code index}
   *
   * @since 0.4.0
   */
  @SuppressWarnings("unchecked")
  int insert(int index, @NotNull StageOrderFunction<S> function)
  {
    if (functions == null)
    {
      functions = (StageOrderFunction<S>[])newInstance(StageOrderFunction.class, 4);
      executionState = new FunctionState[4];
    }
    else if (functions.length == size)
    {
      final var capacity = size + max(40 - (size >> 2), 4);

      functions = copyOf(functions, capacity);
      executionState = copyOf(executionState, capacity);
    }

    final int moveElements = size - index;
    if (moveElements > 0)
    {
      arraycopy(functions, index, functions, index + 1, moveElements);
      arraycopy(executionState, index, executionState, index + 1, moveElements);
    }

    functions[index] = function;
    executionState[index] = WAITING;
    size++;

    return index;
  }


  /**
   * Compares a stage function to be inserted with an existing array entry, ordering by stage first and by order value
   * second. Entries with identical stage and order are treated as smaller than {@code newFunction}, so the new function
   * is positioned after them.
   *
   * @param newFunction    stage function to be inserted, not {@code null}
   * @param arrayFunction  existing array entry, not {@code null}
   *
   * @return  a negative value if {@code newFunction} sorts before {@code arrayFunction}, a positive value otherwise
   */
  @Contract(pure = true)
  private int compare(@NotNull StageOrderFunction<S> newFunction, @NotNull StageOrderFunction<S> arrayFunction)
  {
    int cmp = newFunction.stage.compareTo(arrayFunction.stage);
    if (cmp == 0 && (cmp = Integer.compare(newFunction.order, arrayFunction.order)) == 0)
      cmp = 1;

    return cmp;
  }
}
