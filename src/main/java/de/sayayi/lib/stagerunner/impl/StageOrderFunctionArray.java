package de.sayayi.lib.stagerunner.impl;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;

import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
final class StageOrderFunctionArray<S extends Enum<S>>
{
  StageOrderFunction<S>[] functions;
  int size;


  StageOrderFunctionArray()
  {
    functions = null;
    size = 0;
  }


  StageOrderFunctionArray(@NotNull StageOrderFunctionArray<S> array)
  {
    if (array.functions == null)
    {
      functions = null;
      size = 0;
    }
    else
      functions = copyOf(array.functions, size = array.size);
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
      functions = (StageOrderFunction<S>[])Array.newInstance(StageOrderFunction.class, 4);
    else if (functions.length == size)
      functions = copyOf(functions, size + 4);

    if (low < size)
      arraycopy(functions, low, functions, low + 1, size - low);

    functions[low] = function;
    size++;

    return low;
  }


  @Contract(pure = true)
  private int compare(@NotNull StageOrderFunction<S> newFunction,
                      @NotNull StageOrderFunction<S> arrayFunction)
  {
    int cmp = newFunction.stage.compareTo(arrayFunction.stage);
    if (cmp == 0 && (cmp = Integer.compare(newFunction.order, arrayFunction.order)) == 0)
      cmp = 1;

    return cmp;
  }
}
