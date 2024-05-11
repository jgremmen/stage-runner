package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Map.Entry;

import static de.sayayi.lib.stagerunner.StageContext.State.FINISHED;
import static de.sayayi.lib.stagerunner.StageContext.State.IDLE;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toCollection;


public abstract class AbstractStageContext<S extends Enum<S>,D>
    implements StageContext<S,D>, StageContextAccessor<S>
{
  private final AbstractStageRunnerFactory<S,D,?> stageRunnerFactory;
  private final StageOrderFunctionArray<S,D> functions;
  private final Set<S> processedStages;
  private final D data;
  private State state;

  private int currentFunctionIndex;
  private boolean abort;


  protected <C extends StageContext<S,D>> AbstractStageContext(
      @NotNull AbstractStageRunnerFactory<S,D,?> stageRunnerFactory, D data)
  {
    this.stageRunnerFactory = stageRunnerFactory;
    this.data = data;

    if (stageRunnerFactory.functions.isEmpty())
    {
      functions = new StageOrderFunctionArray<>();
      processedStages = emptySet();
      state = FINISHED;
    }
    else
    {
      functions = new StageOrderFunctionArray<>(stageRunnerFactory.functions);
      processedStages = EnumSet.noneOf(stageRunnerFactory.stageEnumType);
      state = IDLE;
    }

    currentFunctionIndex = 0;
    abort = false;
  }


  @Override
  public D getData() {
    return data;
  }


  @Override
  public @NotNull State getState() {
    return state;
  }


  @Contract(pure = true)
  public boolean isAborted() {
    return abort;
  }


  @Override
  public @NotNull S getCurrentStage()
  {
    if (state == IDLE)
      throw new IllegalStateException("stage runner has not started yet");

    if (state == FINISHED)
      throw new IllegalStateException("stage runner has finished");

    return functions.functions[currentFunctionIndex].stage;
  }


  @Override
  public @NotNull Set<S> getProcessedStages() {
    return unmodifiableSet(processedStages);
  }


  @Override
  public @NotNull Set<S> getRemainingStages()
  {
    if (state == FINISHED)
      return emptySet();

    final Set<S> remainingStages = Arrays
        .stream(functions.functions, currentFunctionIndex, functions.size)
        .map(sof -> sof.stage)
        .distinct()
        .collect(toCollection(() -> EnumSet.noneOf(stageRunnerFactory.stageEnumType)));

    if (state != IDLE)
      remainingStages.remove(functions.functions[currentFunctionIndex].stage);

    return unmodifiableSet(remainingStages);
  }


  @Override
  public void addStageFunction(@NotNull S stage, @NotNull StageFunction<S,D> function, int order)
  {
    if (state == FINISHED)
      throw new IllegalStateException("stage runner has finished");

    final int index = functions.add(new StageOrderFunction<>(stage, order, function));

    if (state != IDLE && index < currentFunctionIndex)
    {
      abort();
      throw new IllegalStateException("stage runner has passed beyond stage " + stage + " and order " + order);
    }
  }


  @Override
  public void abort() {
    abort = true;
  }


  @Override
  public @NotNull Iterator<Entry<S,StageFunction<S,D>>> stageIterator()
  {
    return new Iterator<Entry<S,StageFunction<S,D>>>() {
      @Override
      public boolean hasNext() {
        return currentFunctionIndex < functions.size;
      }

      @Override
      public Entry<S,StageFunction<S,D>> next()
      {
        if (!hasNext())
          throw new NoSuchElementException();

        return functions.functions[currentFunctionIndex++];
      }
    };
  }
}
