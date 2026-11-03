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
import static java.util.Objects.requireNonNull;


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

  /**
   * Callback for the running execution, or {@code null} if not running.
   *
   * @since 0.3.4
   */
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
      processedStages = EnumSet.noneOf(stageRunnerFactory.getStageEnumType());
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
        ? EnumSet.noneOf(stageRunnerFactory.getStageEnumType())
        : EnumSet.copyOf(processedStages);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull Set<S> getRemainingStages()
  {
    var remainingStages = EnumSet.noneOf(stageRunnerFactory.getStageEnumType());

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
   *
   * @throws StageRunnerConfigurationException  if the execution has terminated or has already passed beyond
   *                                            {@code stage} and {@code order}
   * @throws NullPointerException               if {@code stage} or {@code function} is {@code null}
   */
  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function)
  {
    if (state.isTerminated())
      throw new StageRunnerConfigurationException("stage runner has terminated");

    final var stageFunction = new StageOrderFunction<>(stage, description, order, function);
    final var index = functionArray.insertionIndex(stageFunction);

    if (state == RUNNING && (index <= functionIndex || processedStages.contains(stage)))
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
        // inserting accepted functions positioned after the current function does not move the current function,
        // so validating each of them against the unmodified array is sufficient
        validateNamedStageFunction(name, stageFunctionEntry.getValue());
        acceptedFunctions.add(stageFunctionEntry);
      }

    final var enabledFunctions = new HashSet<String>();

    for(var acceptedFunction: acceptedFunctions)
    {
      enableNamedStageFunction(name = acceptedFunction.getKey(), acceptedFunction.getValue());
      enabledFunctions.add(name);
    }

    return enabledFunctions;
  }


  /**
   * {@inheritDoc}
   * <p>
   * The stage function is looked up by its exact name, without copying all named stage functions of the factory.
   */
  @Override
  public boolean enableNamedStageFunction(@NotNull String name)
  {
    if (state.isTerminated())
      throw new StageRunnerConfigurationException("stage runner has terminated");

    if (enabledStageFunctionNames.contains(requireNonNull(name, "name must not be null")))
      return false;

    final var stageFunction = stageRunnerFactory.namedStageFunction(name);
    if (stageFunction == null)
      return false;

    validateNamedStageFunction(name, stageFunction);
    enableNamedStageFunction(name, stageFunction);

    return true;
  }


  /**
   * Validates that the given named stage function can still be enabled for this execution. If the execution has already
   * passed beyond its stage and order, the execution is aborted and an exception is thrown.
   *
   * @param name           stage function name, not {@code null}
   * @param stageFunction  named stage function, not {@code null}
   *
   * @throws StageRunnerConfigurationException  if the execution has passed beyond the stage function
   *
   * @since 0.4.0
   */
  private void validateNamedStageFunction(@NotNull String name, @NotNull StageOrderFunction<S> stageFunction)
  {
    if (state == RUNNING &&
        (functionArray.insertionIndex(stageFunction) <= functionIndex ||
         processedStages.contains(stageFunction.stage)))
    {
      abort();
      throw new StageRunnerConfigurationException("stage runner has passed beyond stage " + stageFunction.stage +
          " and order " + stageFunction.order + " for stage function '" + name + '\'');
    }
  }


  /**
   * Adds the given named stage function to this execution and notifies the callback, if any.
   *
   * @param name           stage function name, not {@code null}
   * @param stageFunction  named stage function, not {@code null}
   *
   * @since 0.4.0
   */
  private void enableNamedStageFunction(@NotNull String name, @NotNull StageOrderFunction<S> stageFunction)
  {
    functionArray.add(stageFunction);
    enabledStageFunctionNames.add(name);

    if (callback != null)
      callback.addStageFunctionCallback(stageFunction.stage, stageFunction.order, stageFunction.description);
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
   * The context runs only once. When the method returns, the context is either {@link State#FINISHED finished} or
   * {@link State#ABORTED aborted}; invoking it again executes no stage function and returns the outcome of the first
   * run. A run terminated by an exception leaves the context aborted, without notifying
   * {@link StageRunnerCallback#postStageCallback(StageContext, Enum) postStageCallback} for the stage being executed.
   *
   * @param callback  stage runner callback, not {@code null}
   *
   * @return  {@code true} if the run completed without being aborted, {@code false} otherwise
   *
   * @throws StageRunnerException  if the context is currently running
   * @throws RuntimeException      any exception thrown by one of the {@code callback} methods
   * @throws Error                 any error thrown by a stage function, rethrown unchanged
   */
  boolean run(@NotNull StageRunnerCallback<S> callback)
  {
    if (state.isTerminated())
      return state == FINISHED;

    if (state != IDLE)
      throw new StageRunnerException("stage runner must be in idle state");

    S lastStage = null;
    var completed = false;

    state = RUNNING;

    try {
      this.callback = callback;

      while(!aborted && ++functionIndex < functionArray.size)
      {
        var stageFunctionEntry = functionArray.functions[functionIndex];
        var currentStage = stageFunctionEntry.stage;

        if (currentStage != lastStage)
        {
          callback.preStageCallback(this);
          lastStage = currentStage;

          if (aborted)
            break;
        }

        functionArray.executionState[functionIndex] = EXECUTING;
        callback.preStageFunctionCallback(this, stageFunctionEntry.description);
        try {
          if (aborted)
            functionArray.executionState[functionIndex] = WAITING;
          else
            stageFunctionEntry.function.process(this);
        } catch(Throwable ex) {
          functionArray.executionState[functionIndex] = FAILED;

          if (ex instanceof Error error)
            throw error;

          if (ex instanceof StageRunnerConfigurationException stageRunnerConfigurationException)
            callback.stageConfigurationExceptionHandler(this, stageRunnerConfigurationException);
          else
            callback.stageExceptionHandler(this, ex);
        } finally {
          if (functionArray.executionState[functionIndex] == EXECUTING)
            functionArray.executionState[functionIndex] = PROCESSED;

          callback.postStageFunctionCallback(this);
        }

        // the stage has completed if no other function of the same stage follows; the callback is notified while the
        // context still points to the last function of the stage, so it reports the completed stage as current stage
        if (!aborted && isLastFunctionOfStage(functionIndex))
        {
          processedStages.add(currentStage);
          callback.postStageCallback(this, currentStage);
        }
      }

      completed = true;
    } finally {
      this.callback = null;

      // a run terminated by an exception has not finished successfully
      state = aborted || !completed ? ABORTED : FINISHED;
    }

    return !aborted;
  }


  /**
   * Tells whether the function at the given index is the last function of its stage.
   *
   * @param index  function index
   *
   * @return  {@code true} if the function at {@code index} is the last function of its stage, {@code false} otherwise
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  private boolean isLastFunctionOfStage(int index)
  {
    return index + 1 == functionArray.size ||
           functionArray.functions[index + 1].stage != functionArray.functions[index].stage;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public @NotNull List<Function<S>> getFunctions()
  {
    final var functions = (Function<S>[])new Function[functionArray.size];

    for(var n = 0; n < functionArray.size; n++)
      functions[n] = new FunctionAdapter<>(functionArray.executionState[n], functionArray.functions[n]);

    return List.of(functions);
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
     * Context execution has been aborted, either by {@link StageContext#abort()}, by a configuration error occurring
     * during the run or by an exception terminating the run.
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
   *
   * @param <S>  stage enumeration type
   */
  @SuppressWarnings("ClassCanBeRecord")
  private static final class FunctionAdapter<S extends Enum<S>> implements Function<S>
  {
    /** Execution state of the function at the time of creation. */
    private final FunctionState functionState;

    /** Stage function descriptor. */
    private final StageOrderFunction<S> stageFunction;


    /**
     * Creates a new function view.
     *
     * @param functionState  execution state of the function at the time of creation, not {@code null}
     * @param stageFunction  stage function descriptor, not {@code null}
     */
    private FunctionAdapter(@NotNull FunctionState functionState,
                            @NotNull StageOrderFunction<S> stageFunction)
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
    public @NotNull S getStage() {
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
      final var s = new StringBuilder("Function(state=").append(getFunctionState())
          .append(",stage=").append(getStage().name()).append('#').append(getOrder());

      final var description = getDescription();
      if (description != null && !description.isEmpty())
        s.append(",description=").append(getDescription());

      return s.append(')').toString();
    }
  }
}
