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
import java.util.Map.Entry;
import java.util.function.Predicate;

import static de.sayayi.lib.stagerunner.StageContext.FunctionState.*;
import static de.sayayi.lib.stagerunner.spi.StageContextImpl.State.*;
import static java.util.Collections.emptySet;


/**
 * Default {@link StageContext} implementation used by {@link AbstractStageRunner}.
 * <p>
 * Each instance represents a single execution of a stage runner. It holds the working copy of the factory's stage
 * function configuration, tracks which stages have already been processed and drives the callback notifications while
 * iterating through the stage functions. Once the context has finished or has been aborted, it cannot be reused.
 * <p>
 * Instances are not thread safe and must only be used by the thread executing the stage runner.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
final class StageContextImpl<S extends Enum<S>> implements StageContext<S>
{
  /** Factory this context has been created for. */
  private final AbstractStageRunnerFactory<S> stageRunnerFactory;

  /** Working copy of the stage functions for this execution. */
  private final StageOrderFunctionArray<S> functionArray;

  /** Stages which have been processed completely. */
  private final Set<S> processedStages;

  /** Data passed to the stage runner. */
  private final Map<String,Object> data;

  /** Names of the named stage functions enabled for this execution. */
  private final Set<String> enabledStageFunctionNames;

  /** Callback for the running execution, or {@code null} if not running. */
  private StageRunnerCallback<S> callback;

  /** Lifecycle state of this context. */
  private State state;

  /** Index of the stage function currently being executed, or {@code -1} before the first one. */
  private int functionIndex;

  /** Whether the execution has been aborted. */
  private boolean aborted;


  /**
   * Creates a new stage context for a single run, based on a snapshot of the factory's current stage function
   * configuration.
   *
   * @param stageRunnerFactory  factory providing the shared stage function configuration, not {@code null}
   * @param data                data map accessible from stage functions through {@link #getData(String)}, not
   *                            {@code null}
   */
  StageContextImpl(@NotNull AbstractStageRunnerFactory<S> stageRunnerFactory, @NotNull Map<String,Object> data)
  {
    this.stageRunnerFactory = stageRunnerFactory;
    this.data = data;

    functionArray = stageRunnerFactory.functionArray();

    if (functionArray.size == 0)
    {
      processedStages = emptySet();
      state = FINISHED;
    }
    else
    {
      processedStages = EnumSet.noneOf(stageRunnerFactory.stageEnumType);
      state = IDLE;
    }

    functionIndex = -1;
    aborted = false;
    enabledStageFunctionNames = new HashSet<>();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getData(@NotNull String name) {
    return (T)data.get(name);
  }


  /**
   * {@inheritDoc}
   */
  @Contract(pure = true)
  public boolean isAborted() {
    return aborted;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull S getCurrentStage()
  {
    if (state == IDLE || functionIndex == -1)
      throw new StageRunnerException("stage runner has not started yet");

    if (state.isTerminated())
      throw new StageRunnerException("stage runner has terminated");

    return functionArray.functions[functionIndex].stage;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull Set<S> getProcessedStages()
  {
    return processedStages.isEmpty()
        ? EnumSet.noneOf(stageRunnerFactory.stageEnumType)
        : EnumSet.copyOf(processedStages);
  }


  /**
   * {@inheritDoc}
   */
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


  /**
   * {@inheritDoc}
   * <p>
   * The stage function only applies to this execution. If it would be positioned before the stage function currently
   * being executed, it is not added and the execution is aborted before the exception is thrown. If a callback is
   * active, it is notified through {@link StageRunnerCallback#addStageFunctionCallback(Enum, int, String)}.
   */
  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function)
  {
    if (state.isTerminated())
      throw new StageRunnerConfigurationException("stage runner has terminated");

    final var stageFunction = new StageOrderFunction<>(stage, description, order, function);
    final var index = functionArray.insertionIndex(stageFunction);

    if (state == RUNNING && index <= functionIndex)
    {
      abort();
      throw new StageRunnerConfigurationException("stage runner has passed beyond stage " + stage + " and order " + order);
    }

    functionArray.insert(index, stageFunction);

    if (callback != null)
      callback.addStageFunctionCallback(stage, order, description);
  }


  /**
   * {@inheritDoc}
   * <p>
   * All accepted stage functions are validated before any of them is enabled. If an accepted stage function would be
   * positioned before the stage function currently being executed, none of them is enabled and the execution is
   * aborted before the exception is thrown. If a callback is active, it is notified through
   * {@link StageRunnerCallback#addStageFunctionCallback(Enum, int, String)} for each enabled function.
   */
  @Override
  public @NotNull Set<String> enableNamedStageFunctions(@NotNull Predicate<String> nameFilter)
  {
    if (state.isTerminated())
      throw new StageRunnerConfigurationException("stage runner has terminated");

    final var acceptedFunctions = new ArrayList<Entry<String,StageOrderFunction<S>>>();
    String name;

    for(var stageFunctionEntry: stageRunnerFactory.namedStageFunctions().entrySet())
      if (!enabledStageFunctionNames.contains(name = stageFunctionEntry.getKey()) && nameFilter.test(name))
      {
        final var stageFunction = stageFunctionEntry.getValue();

        // inserting accepted functions positioned after the current function does not move the current function,
        // so validating each of them against the unmodified array is sufficient
        if (state == RUNNING && functionArray.insertionIndex(stageFunction) <= functionIndex)
        {
          abort();
          throw new StageRunnerConfigurationException("stage runner has passed beyond stage " + stageFunction.stage +
              " and order " + stageFunction.order + " for stage function '" + name + '\'');
        }

        acceptedFunctions.add(stageFunctionEntry);
      }

    final var enabledFunctions = new HashSet<String>();

    for(var acceptedFunction: acceptedFunctions)
    {
      var stageFunction = acceptedFunction.getValue();

      functionArray.add(stageFunction);
      enabledFunctions.add(name = acceptedFunction.getKey());
      enabledStageFunctionNames.add(name);

      if (callback != null)
        callback.addStageFunctionCallback(stageFunction.stage, stageFunction.order, stageFunction.description);
    }

    return enabledFunctions;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void abort() {
    aborted = true;
  }


  /**
   * Runs all configured stage functions in order, notifying the given callback about stage and function transitions as
   * well as failures.
   * <p>
   * The context must be in {@link State#IDLE idle} state. When the method returns, the context is either
   * {@link State#FINISHED finished} or {@link State#ABORTED aborted} and cannot be run again.
   *
   * @param callback  stage runner callback, not {@code null}
   *
   * @return  {@code true} if the run completed without being aborted, {@code false} otherwise
   *
   * @throws StageRunnerException  if the context is currently running
   * @throws RuntimeException      any exception thrown by one of the {@code callback} methods
   */
  boolean run(@NotNull StageRunnerCallback<S> callback)
  {
    if (state.isTerminated())
      return !aborted;

    if (state != IDLE)
      throw new StageRunnerException("stage runner must be in idle state");

    S lastStage = null;

    state = RUNNING;

    try {
      this.callback = callback;

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
          if (ex instanceof StageRunnerConfigurationException stageRunnerConfigurationException)
            callback.stageConfigurationExceptionHandler(this, stageRunnerConfigurationException);
          else
            callback.stageExceptionHandler(this, ex);
        } finally {
          if (functionArray.executionState[functionIndex] == EXECUTING)
            functionArray.executionState[functionIndex] = PROCESSED;

          callback.postStageFunctionCallback(this);
        }
      }
    } finally {
      this.callback = null;

      state = aborted ? ABORTED : FINISHED;

      if (!aborted && lastStage != null)
      {
        processedStages.add(lastStage);
        callback.postStageCallback(this, lastStage);
      }
    }

    return !aborted;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull List<Function> getFunctions()
  {
    var functions = new Function[functionArray.size];

    for(int n = 0; n < functionArray.size; n++)
      functions[n] = new FunctionAdapter(functionArray.executionState[n], functionArray.functions[n]);

    return Arrays.asList(functions);
  }




  /**
   * Lifecycle state of a {@link StageContextImpl}.
   */
  enum State
  {
    /** Context has been created but has not started running yet. */
    IDLE,

    /** Context is currently executing stage functions. */
    RUNNING,

    /** Context has finished executing all stage functions successfully. */
    FINISHED,

    /**
     * Context execution has been aborted, either by {@link StageContext#abort()} or by a configuration error occurring
     * during the run.
     */
    ABORTED;


    /**
     * Tells whether this state represents a terminated context that can no longer run.
     *
     * @return  {@code true} if the state is {@link #FINISHED} or {@link #ABORTED}, {@code false} otherwise
     */
    @Contract(pure = true)
    boolean isTerminated() {
      return this == FINISHED || this == ABORTED;
    }
  }




  /**
   * Adapter exposing a {@link StageOrderFunction} together with its current execution state through the public
   * {@link Function} view returned by {@link StageContext#getFunctions()}.
   */
  @SuppressWarnings("ClassCanBeRecord")
  private static final class FunctionAdapter implements Function
  {
    /** Execution state of the function at the time of creation. */
    private final FunctionState functionState;

    /** Stage function descriptor. */
    private final StageOrderFunction<?> stageFunction;


    /**
     * Creates a new function view.
     *
     * @param functionState  execution state of the function at the time of creation, not {@code null}
     * @param stageFunction  stage function descriptor, not {@code null}
     */
    private FunctionAdapter(@NotNull FunctionState functionState,
                            @NotNull StageOrderFunction<?> stageFunction)
    {
      this.functionState = functionState;
      this.stageFunction = stageFunction;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull FunctionState getFunctionState() {
      return functionState;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Enum<?> getStage() {
      return stageFunction.stage;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
      return stageFunction.order;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
      return stageFunction.description;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      var s = new StringBuilder("Function(state=").append(getFunctionState())
          .append(",stage=").append(getStage().name()).append('#').append(getOrder());

      final var description = getDescription();
      if (description != null && !description.isEmpty())
        s.append(",description=").append(getDescription());

      return s.append(')').toString();
    }
  }
}
