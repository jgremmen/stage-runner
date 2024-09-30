package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;

import static de.sayayi.lib.stagerunner.impl.StageContextImpl.State.*;
import static java.util.Collections.emptySet;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
final class StageContextImpl<S extends Enum<S>> implements StageContext<S>
{
  private final AbstractStageRunnerFactory<S> stageRunnerFactory;
  private final StageOrderFunctionArray<S> functionArray;
  private final Set<S> processedStages;
  private final Map<String,Object> data;
  private final Set<String> enabledStageFunctionNames;

  private State state;
  private int nextFunctionIndex;
  private boolean aborted;


  StageContextImpl(@NotNull AbstractStageRunnerFactory<S> stageRunnerFactory, @NotNull Map<String,Object> data)
  {
    this.stageRunnerFactory = stageRunnerFactory;
    this.data = data;

    if (stageRunnerFactory.functionArray.size == 0)
    {
      functionArray = new StageOrderFunctionArray<>();
      processedStages = emptySet();
      state = FINISHED;
    }
    else
    {
      functionArray = new StageOrderFunctionArray<>(stageRunnerFactory.functionArray);
      processedStages = EnumSet.noneOf(stageRunnerFactory.stageEnumType);
      state = IDLE;
    }

    nextFunctionIndex = 0;
    aborted = false;
    enabledStageFunctionNames = new HashSet<>();
  }


  @Override
  @SuppressWarnings("unchecked")
  public <T> T getData(@NotNull String name) {
    return (T)data.get(name);
  }


  @Contract(pure = true)
  public boolean isAborted() {
    return aborted;
  }


  @Override
  public @NotNull S getCurrentStage()
  {
    if (state == IDLE || nextFunctionIndex == 0)
      throw new StageRunnerException("stage runner has not started yet");

    if (state.isTerminated())
      throw new StageRunnerException("stage runner has terminated");

    return functionArray.functions[nextFunctionIndex - 1].stage;
  }


  @Override
  public @NotNull Set<S> getProcessedStages()
  {
    return processedStages.isEmpty()
        ? EnumSet.noneOf(stageRunnerFactory.stageEnumType)
        : EnumSet.copyOf(processedStages);
  }


  @Override
  public @NotNull Set<S> getRemainingStages()
  {
    final Set<S> remainingStages = EnumSet.noneOf(stageRunnerFactory.stageEnumType);

    if (!state.isTerminated())
    {
      Arrays
          .stream(functionArray.functions, nextFunctionIndex, functionArray.size)
          .map(stageOrderFunction -> stageOrderFunction.stage)
          .forEach(remainingStages::add);

      if (state == RUNNING && nextFunctionIndex > 0)
        remainingStages.remove(functionArray.functions[nextFunctionIndex - 1].stage);
    }

    return remainingStages;
  }


  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function)
  {
    if (state.isTerminated())
      throw new StageRunnerException("stage runner has terminated");

    final int index = functionArray.add(new StageOrderFunction<>(stage, description, order, function));

    if (state == RUNNING && index < nextFunctionIndex)
    {
      abort();
      throw new StageRunnerException("stage runner has passed beyond stage " + stage + " and order " + order);
    }
  }


  @Override
  public @NotNull Set<String> enableNamedStageFunctions(@NotNull Predicate<String> nameFilter)
  {
    if (state.isTerminated())
      throw new StageRunnerException("stage runner has terminated");

    final Set<String> enabledFunctions = new HashSet<>();
    String name;

    for(Entry<String,StageOrderFunction<S>> stageFunctionEntry: stageRunnerFactory.namedStageFunctions.entrySet())
      if (!enabledStageFunctionNames.contains(name = stageFunctionEntry.getKey()) && nameFilter.test(name))
      {
        final StageOrderFunction<S> stageFunction = stageFunctionEntry.getValue();
        final int index = functionArray.add(stageFunction);

        if (state == RUNNING && index < nextFunctionIndex)
        {
          abort();
          throw new StageRunnerException("stage runner has passed beyond stage " + stageFunction.stage +
              " and order " + stageFunction.order + " for stage function '" + name + '\'');
        }

        stageFunctionEntry.setValue(null);
        enabledFunctions.add(name);
        enabledStageFunctionNames.add(name);
      }

    return enabledFunctions;
  }


  @Override
  public void abort() {
    aborted = true;
  }


  boolean run(@NotNull StageRunnerCallback<S> callback)
  {
    if (state.isTerminated())
      return !aborted;

    if (state != IDLE)
      throw new StageRunnerException("stage runner must be in idle state");

    S lastStage = null;

    state = RUNNING;

    try {
      while(!aborted && nextFunctionIndex < functionArray.size)
      {
        final StageOrderFunction<S> stageFunctionEntry = functionArray.functions[nextFunctionIndex++];
        final S currentStage = stageFunctionEntry.stage;

        if (currentStage != lastStage)
        {
          if (lastStage != null)
          {
            processedStages.add(lastStage);
            callback.postStageCallback(this, lastStage);
          }

          lastStage = null;
          callback.preStageCallback(this);
          lastStage = currentStage;
        }

        callback.preStageFunctionCallback(this,
            functionArray.functions[nextFunctionIndex - 1].description);
        try {
          stageFunctionEntry.function.process(this);
        } catch(Throwable ex) {
          callback.stageExceptionHandler(this, ex);
        } finally {
          callback.postStageFunctionCallback(this);
        }
      }
    } finally {
      state = aborted ? State.ABORTED : FINISHED;

      if (!aborted && lastStage != null)
      {
        processedStages.add(lastStage);
        callback.postStageCallback(this, lastStage);
      }
    }

    return !aborted;
  }




  enum State
  {
    IDLE,
    RUNNING,
    FINISHED,
    ABORTED;


    @Contract(pure = true)
    boolean isTerminated() {
      return this == FINISHED || this == ABORTED;
    }
  }
}
