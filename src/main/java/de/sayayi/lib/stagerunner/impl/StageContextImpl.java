package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static de.sayayi.lib.stagerunner.StageContext.State.*;
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

  private int currentFunctionIndex;
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

    currentFunctionIndex = 0;
    abort = false;
  }


  @Override
  @SuppressWarnings("unchecked")
  public <T> T getData(@NotNull String name) {
    return (T)data.get(name);
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

    if (state == IDLE || state == RUNNING)
    {
      Arrays
          .stream(functions.functions, currentFunctionIndex, functions.size)
          .map(stageOrderFunction -> stageOrderFunction.stage)
          .forEach(remainingStages::add);

      if (state == RUNNING)
        remainingStages.remove(functions.functions[currentFunctionIndex].stage);
    }

    return remainingStages;
  }


  @Override
  public void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function, int order)
  {
    if (state == FINISHED || state == ABORTED)
      throw new IllegalStateException("stage runner has finished");

    final int index = functions.add(new StageOrderFunction<>(stage, order, function));

    if (state == RUNNING && index < currentFunctionIndex)
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


  void run(@NotNull StageRunner<S> stageRunner)
  {
    if (state == FINISHED)
      return;

    if (state != IDLE)
      throw new IllegalStateException();

    S lastStage = null;

    state = RUNNING;

    try {
      while(!abort && currentFunctionIndex < functions.size)
      {
        final StageOrderFunction<S> stageFunctionEntry = functions.functions[currentFunctionIndex++];
        final S currentStage = stageFunctionEntry.stage;

        if (currentStage != lastStage)
        {
          if (lastStage != null)
            stageRunner.postStageCallback(this, lastStage);

          lastStage = null;
          stageRunner.preStageCallback(this);
          lastStage = currentStage;
        }

        stageRunner.preStageFunctionCallback(this);
        try {
          stageFunctionEntry.function.process(this);
        } catch(Throwable ex) {
          stageRunner.stageExceptionHandler(this, ex);
        } finally {
          stageRunner.postStageFunctionCallback(this);
        }
      }
    } finally {
      state = abort ? ABORTED : FINISHED;

      if (!abort && lastStage != null)
        stageRunner.postStageCallback(this, lastStage);
    }
  }
}
