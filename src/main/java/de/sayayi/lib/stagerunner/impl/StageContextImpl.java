package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static de.sayayi.lib.stagerunner.impl.StageContextImpl.State.*;
import static java.util.Collections.emptySet;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public class StageContextImpl<S extends Enum<S>> implements StageContext<S>
{
  private final AbstractStageRunnerFactory<S> stageRunnerFactory;
  private final StageOrderFunctionArray<S> functions;
  private final Set<S> processedStages;
  private final Map<String,Object> data;
  private State state;

  private int nextFunctionIndex;
  private boolean abort;


  protected StageContextImpl(@NotNull AbstractStageRunnerFactory<S> stageRunnerFactory,
                             @NotNull Map<String,Object> data)
  {
    this.stageRunnerFactory = stageRunnerFactory;
    this.data = data;

    if (stageRunnerFactory.functions.size == 0)
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

    nextFunctionIndex = 0;
    abort = false;
  }


  @Override
  @SuppressWarnings("unchecked")
  public <T> T getData(@NotNull String name) {
    return (T)data.get(name);
  }


  @Contract(pure = true)
  public boolean isAborted() {
    return abort;
  }


  @Override
  public @NotNull S getCurrentStage()
  {
    if (state == IDLE || nextFunctionIndex == 0)
      throw new IllegalStateException("stage runner has not started yet");

    if (state.isTerminated())
      throw new IllegalStateException("stage runner has terminated");

    return functions.functions[nextFunctionIndex - 1].stage;
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
          .stream(functions.functions, nextFunctionIndex, functions.size)
          .map(stageOrderFunction -> stageOrderFunction.stage)
          .forEach(remainingStages::add);

      if (state == RUNNING && nextFunctionIndex > 0)
        remainingStages.remove(functions.functions[nextFunctionIndex - 1].stage);
    }

    return remainingStages;
  }


  @Override
  public void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function, int order)
  {
    if (state.isTerminated())
      throw new IllegalStateException("stage runner has terminated");

    final int index = functions.add(new StageOrderFunction<>(stage, order, function));

    if (state == RUNNING && index < nextFunctionIndex)
    {
      abort();
      throw new IllegalStateException("stage runner has passed beyond stage " + stage +
          " and order " + order);
    }
  }


  @Override
  public void abort() {
    abort = true;
  }


  boolean run(@NotNull StageRunnerCallback<S> callback)
  {
    if (state.isTerminated())
      return true;

    if (state != IDLE)
      throw new IllegalStateException();

    S lastStage = null;

    state = RUNNING;

    try {
      while(!abort && nextFunctionIndex < functions.size)
      {
        final StageOrderFunction<S> stageFunctionEntry = functions.functions[nextFunctionIndex++];
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

        callback.preStageFunctionCallback(this);
        try {
          stageFunctionEntry.function.process(this);
        } catch(Throwable ex) {
          callback.stageExceptionHandler(this, ex);
        } finally {
          callback.postStageFunctionCallback(this);
        }
      }
    } finally {
      state = abort ? State.ABORTED : FINISHED;

      if (!abort && lastStage != null)
      {
        processedStages.add(lastStage);
        callback.postStageCallback(this, lastStage);
      }
    }

    return !abort;
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
