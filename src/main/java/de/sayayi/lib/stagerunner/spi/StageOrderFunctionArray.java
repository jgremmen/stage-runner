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
import static java.lang.System.arraycopy;
import static java.lang.reflect.Array.newInstance;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.fill;


/**
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 */
final class StageOrderFunctionArray<S extends Enum<S>>
{
  StageOrderFunction<S>[] functions;
  FunctionState[] executionState;
  int size;


  StageOrderFunctionArray()
  {
    functions = null;
    executionState = null;
    size = 0;
  }


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


  @SuppressWarnings("unchecked")
  int add(@NotNull StageOrderFunction<S> function)
  {
    int low = 0;

    for(int high = size - 1; low <= high;)
    {
      final int mid = (low + high) >>> 1;

      if (compare(function, functions[mid]) > 0)
        low = mid + 1;
      else
        high = mid - 1;
    }

    if (functions == null)
    {
      functions = (StageOrderFunction<S>[])newInstance(StageOrderFunction.class, 4);
      executionState = new FunctionState[4];
    }
    else if (functions.length == size)
    {
      functions = copyOf(functions, size + 4);
      executionState = copyOf(executionState, size + 4);
    }

    final int moveElements = size - low;
    if (moveElements > 0)
    {
      arraycopy(functions, low, functions, low + 1, moveElements);
      arraycopy(executionState, low, executionState, low + 1, moveElements);
    }

    functions[low] = function;
    executionState[low] = WAITING;
    size++;

    return low;
  }


  @Contract(pure = true)
  private int compare(@NotNull StageOrderFunction<S> newFunction, @NotNull StageOrderFunction<S> arrayFunction)
  {
    int cmp = newFunction.stage.compareTo(arrayFunction.stage);
    if (cmp == 0 && (cmp = Integer.compare(newFunction.order, arrayFunction.order)) == 0)
      cmp = 1;

    return cmp;
  }
}
