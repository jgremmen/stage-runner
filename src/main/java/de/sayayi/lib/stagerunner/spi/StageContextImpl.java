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

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.exception.StageRunnerException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

import static de.sayayi.lib.stagerunner.StageContext.FunctionState.*;
import static de.sayayi.lib.stagerunner.spi.StageContextImpl.State.*;
import static java.util.Collections.emptySet;


/**
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 */
final class StageContextImpl<S extends Enum<S>> implements StageContext<S>
{
  private final AbstractStageRunnerFactory<S> stageRunnerFactory;
  private final StageOrderFunctionArray<S> functionArray;
  private final Set<S> processedStages;
  private final Map<String,Object> data;
  private final Set<String> enabledStageFunctionNames;

  private State state;
  private int functionIndex;
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

    functionIndex = -1;
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
    if (state == IDLE || functionIndex == -1)
      throw new StageRunnerException("stage runner has not started yet");

    if (state.isTerminated())
      throw new StageRunnerException("stage runner has terminated");

    return functionArray.functions[functionIndex].stage;
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
    var remainingStages = EnumSet.noneOf(stageRunnerFactory.stageEnumType);

    if (!state.isTerminated())
    {
      Arrays
          .stream(functionArray.functions, functionIndex + 1, functionArray.size)
          .map(stageOrderFunction -> stageOrderFunction.stage)
          .forEach(remainingStages::add);

      if (state == RUNNING && functionIndex >= 0)
        remainingStages.remove(functionArray.functions[functionIndex].stage);
    }

    return remainingStages;
  }


  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function)
  {
    if (state.isTerminated())
      throw new StageRunnerConfigurationException("stage runner has terminated");

    final int index = functionArray.add(new StageOrderFunction<>(stage, description, order, function));

    if (state == RUNNING && index <= functionIndex)
    {
      abort();
      throw new StageRunnerConfigurationException("stage runner has passed beyond stage " + stage + " and order " + order);
    }
  }


  @Override
  public @NotNull Set<String> enableNamedStageFunctions(@NotNull Predicate<String> nameFilter)
  {
    if (state.isTerminated())
      throw new StageRunnerConfigurationException("stage runner has terminated");

    var enabledFunctions = new HashSet<String>();
    String name;

    for(var stageFunctionEntry: stageRunnerFactory.namedStageFunctions.entrySet())
      if (!enabledStageFunctionNames.contains(name = stageFunctionEntry.getKey()) && nameFilter.test(name))
      {
        var stageFunction = stageFunctionEntry.getValue();
        var index = functionArray.add(stageFunction);

        if (state == RUNNING && index <= functionIndex)
        {
          abort();
          throw new StageRunnerConfigurationException("stage runner has passed beyond stage " + stageFunction.stage +
              " and order " + stageFunction.order + " for stage function '" + name + '\'');
        }

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
      while(!aborted && ++functionIndex < functionArray.size)
      {
        var stageFunctionEntry = functionArray.functions[functionIndex];
        var currentStage = stageFunctionEntry.stage;

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

        functionArray.executionState[functionIndex] = EXECUTING;
        callback.preStageFunctionCallback(this, stageFunctionEntry.description);
        try {
          stageFunctionEntry.function.process(this);
        } catch(Throwable ex) {
          functionArray.executionState[functionIndex] = FAILED;
          callback.stageExceptionHandler(this, ex);
        } finally {
          if (functionArray.executionState[functionIndex] == EXECUTING)
            functionArray.executionState[functionIndex] = PROCESSED;

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


  @Override
  public @NotNull List<Function> getFunctions()
  {
    var functions = new Function[functionArray.size];

    for(int n = 0; n < functionArray.size; n++)
      functions[n] = new FunctionAdapter(functionArray.executionState[n], functionArray.functions[n]);

    return Arrays.asList(functions);
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




  private static final class FunctionAdapter implements Function
  {
    private final FunctionState functionState;
    private final StageOrderFunction<?> stageFunction;


    private FunctionAdapter(@NotNull FunctionState functionState,
                            @NotNull StageOrderFunction<?> stageFunction)
    {
      this.functionState = functionState;
      this.stageFunction = stageFunction;
    }


    @Override
    public @NotNull FunctionState getFunctionState() {
      return functionState;
    }


    @Override
    public @NotNull Enum<?> getStage() {
      return stageFunction.stage;
    }


    @Override
    public int getOrder() {
      return stageFunction.order;
    }


    @Override
    public String getDescription() {
      return stageFunction.description;
    }


    @Override
    public String toString()
    {
      var s = new StringBuilder("Function(state=").append(getFunctionState())
          .append(",stage=").append(getStage().name()).append('#').append(getOrder());

      final String description = getDescription();
      if (description != null && !description.isEmpty())
        s.append(",description=").append(getDescription());

      return s.append(')').toString();
    }
  }
}
