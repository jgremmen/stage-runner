package de.sayayi.lib.stagerunner.impl;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;


final class StageOrderFunctionArray<S extends Enum<S>,D>
{
  StageOrderFunction<S,D>[] functions;
  int size;


  StageOrderFunctionArray()
  {
    functions = null;
    size = 0;
  }


  StageOrderFunctionArray(@NotNull StageOrderFunctionArray<S,D> array)
  {
    functions = array.functions == null ? null : array.functions.clone();
    size = array.size;
  }


  @Contract(pure = true)
  boolean isEmpty() {
    return size == 0;
  }


  int add(@NotNull StageOrderFunction<S,D> function)
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

    if (functions.length == size)
      functions = copyOf(functions, size + 8);

    if (low < size)
      arraycopy(functions, low, functions, low + 1, size - low);

    functions[low] = function;
    size++;

    return low;
  }


  @Contract(pure = true)
  private int compare(@NotNull StageOrderFunction<S,D> newFunction,
                      @NotNull StageOrderFunction<S,D> arrayFunction)
  {
    final int cmp = newFunction.stage.compareTo(arrayFunction.stage);
    return cmp == 0 ? Integer.compare(newFunction.order * 2 + 1, arrayFunction.order * 2) : cmp;
  }
}
