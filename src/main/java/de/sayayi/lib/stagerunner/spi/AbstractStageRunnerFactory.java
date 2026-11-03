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

import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageFunctionConfigurer;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.Objects.requireNonNull;


/**
 * Abstract base class for stage runner factories.
 * <p>
 * It maintains the shared configuration used by every runner produced by this factory: the ordered set of stage
 * functions that run for every invocation and the pool of named stage functions that individual runners can selectively
 * enable at runtime.
 * <p>
 * Subclasses only need to implement {@link #createRunner()} in order to produce runner instances that operate on this
 * shared configuration.
 * <p>
 * The configuration methods are thread safe. Regular stage functions added while a runner is being executed do not
 * affect that execution; they apply to executions started afterwards. Named stage functions become available for
 * enabling immediately.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public abstract class AbstractStageRunnerFactory<S extends Enum<S>>
    implements StageRunnerFactory<S>, StageFunctionConfigurer<S>, StageFunctionConfigurer.Named<S>
{
  /** Stage enum class of the stages handled by this factory, never {@code null}. */
  private final Class<S> stageEnumType;

  /** Regular stage functions, executed for every stage runner execution. */
  private final StageOrderFunctionArray<S> functionArray;

  /** Named stage functions keyed by name in registration order, executed only if enabled. */
  private final Map<String,StageOrderFunction<S>> namedStageFunctions;

  /**
   * Lock guarding {@link #functionArray} and {@link #namedStageFunctions}.
   *
   * @since 0.4.0
   */
  private final ReentrantReadWriteLock configurationLock = new ReentrantReadWriteLock();


  /**
   * Creates a new stage runner factory for the given stage enum type.
   *
   * @param stageEnumType  stage enum class, not {@code null}
   *
   * @throws NullPointerException  if {@code stageEnumType} is {@code null}
   */
  protected AbstractStageRunnerFactory(@NotNull Class<S> stageEnumType)
  {
    this.stageEnumType = requireNonNull(stageEnumType, "stageEnumType must not be null");

    functionArray = new StageOrderFunctionArray<>();
    namedStageFunctions = new LinkedHashMap<>();
  }


  /**
   * Returns the stage enum class of the stages handled by this factory.
   *
   * @return  stage enum class, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  protected @NotNull Class<S> getStageEnumType() {
    return stageEnumType;
  }


  /**
   * {@inheritDoc}
   * <p>
   * The stage function applies to all subsequently started stage runner executions.
   *
   * @throws NullPointerException  if {@code stage} or {@code function} is {@code null}
   */
  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function)
  {
    final var writeLock = configurationLock.writeLock();

    writeLock.lock();
    try {
      functionArray.add(new StageOrderFunction<>(stage, description, order, function));
    } finally {
      writeLock.unlock();
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * The name must be unique for this factory.
   *
   * @throws StageRunnerConfigurationException  if {@code name} is empty or a named stage function with the same name
   *                                            has already been registered with this factory
   * @throws NullPointerException               if {@code name}, {@code stage} or {@code function} is {@code null}
   */
  @Override
  public void namedStageFunction(@NotNull String name, @NotNull S stage, int order, String description,
                                 @NotNull StageFunction<S> function)
  {
    if (requireNonNull(name, "name must not be null").isEmpty())
      throw new StageRunnerConfigurationException("name must not be empty");

    final var writeLock = configurationLock.writeLock();

    writeLock.lock();
    try {
      if (namedStageFunctions.containsKey(name))
        throw new StageRunnerConfigurationException("name '" + name + "' must be unique for this stage runner factory");

      namedStageFunctions.put(name, new StageOrderFunction<>(stage, description, order, function));
    } finally {
      writeLock.unlock();
    }
  }


  /**
   * Creates a consistent copy of the current stage function array, safe to hand to a new stage runner even if
   * {@link #addStageFunction} is invoked concurrently on another thread.
   *
   * @return  copy of the current stage function array, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  @NotNull StageOrderFunctionArray<S> functionArray()
  {
    final var readLock = configurationLock.readLock();

    readLock.lock();
    try {
      return new StageOrderFunctionArray<>(functionArray);
    } finally {
      readLock.unlock();
    }
  }


  /**
   * Returns the named stage function with the given name.
   *
   * @param name  stage function name, not {@code null}
   *
   * @return  named stage function or {@code null} if no stage function with {@code name} has been registered
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  StageOrderFunction<S> namedStageFunction(@NotNull String name)
  {
    final var readLock = configurationLock.readLock();

    readLock.lock();
    try {
      return namedStageFunctions.get(name);
    } finally {
      readLock.unlock();
    }
  }


  /**
   * Creates a consistent copy of the current named stage functions, safe to iterate even if {@link #namedStageFunction}
   * is invoked concurrently on another thread.
   *
   * @return  copy of the current named stage functions, keyed by name in registration order, never {@code null}
   *
   * @since 0.4.0
   */
  @Contract(pure = true)
  @NotNull Map<String,StageOrderFunction<S>> namedStageFunctions()
  {
    final var readLock = configurationLock.readLock();

    readLock.lock();
    try {
      return new LinkedHashMap<>(namedStageFunctions);
    } finally {
      readLock.unlock();
    }
  }
}
