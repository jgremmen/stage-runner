package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


/**
 * The stage context interface provides information about the stage runner:
 * <ul>
 *   <li>Data associated with this stage runner instance</li>
 *   <li>Current stage</li>
 *   <li>Processed and remaining stages</li>
 *   <li>Methods for adding future stage functions</li>
 *   <li>Methods for enabling pre-defined named stage functions</li>
 *   <li>{@link #abort() Abort} function</li>
 * </ul>
 *
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public interface StageContext<S extends Enum<S>> extends StageFunctionConfigurer<S>
{
  /**
   * Returns the stage data associated with {@code name}.
   *
   * @param name  data name, not {@code null}
   *
   * @return  data value for name {@code name} or null if no data is available
   *
   * @param <T>  data type
   *
   * @see StageRunner#run(Map)
   */
  @Contract(pure = true)
  <T> T getData(@NotNull String name);


  /**
   * Returns the state being currently processed.
   *
   * @return  state currently being processed, never {@code null}
   *
   * @throws IllegalStateException  if the stage runner hasn't started yet or has finished
   */
  @Contract(pure = true)
  @NotNull S getCurrentStage();


  /**
   * Returns a set with stages which have been processed. The current stage is never part of this
   * collection.
   *
   * @return  set with processed stages, never {@code null}
   */
  @Contract(pure = true)
  @NotNull Set<S> getProcessedStages();


  /**
   * Returns a set with the remaining stages. The current stage is never part of this collection.
   *
   * @return  set with remaining stages, never {@code null}
   */
  @Contract(pure = true)
  @NotNull Set<S> getRemainingStages();


  /**
   * Abort stage context running.
   */
  void abort();


  /**
   * Tell, whether the stage runner has been aborted.
   *
   * @return  {@code true} if the stage runner has been aborted due to an invocation of {@link #abort()},
   *          {@code false} otherwise
   *
   * @see #abort()
   */
  @Contract(pure = true)
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean isAborted();


  /**
   * Enable a named stage function by providing its exact {@code name}.
   *
   * @param name  name of the stage function to enabler
   *
   * @return  {@code true} if a stage function with {@code name} exists and has been enabled by this invocation,
   *          {@code false} otherwise
   *
   * @since 0.3.0
   * 
   * @see #enableNamedStageFunctions(Predicate) 
   */
  default boolean enableNamedStageFunction(@NotNull String name) {
    return !enableNamedStageFunctions(n -> n.equals(name)).isEmpty();
  }


  /**
   * Enable named stage functions by providing a name filter.
   *
   * @param nameFilter  name filter predicate. A stage function is added if and only if the predicate
   *                    ({@link Predicate#test(Object)}) returns {@code true}
   *
   * @return  a set with the stage function names, which have been enabled as a result of this invocation,
   *          never {@code null}
   *
   * @since 0.3.0
   */
  @NotNull Set<String> enableNamedStageFunctions(@NotNull Predicate<String> nameFilter);


  /**
   * Returns a list of all active functions. Each function provides the {@link FunctionState state} it is currently in.
   *
   * @return  immutable list of all active functions, never {@code null}
   *
   * @since 0.3.0
   */
  @Contract(pure = true)
  @NotNull List<Function> getFunctions();




  /**
   * @since 0.3.0
   */
  interface Function
  {
    /**
     * Return the function state for this function.
     *
     * @return  function state, never {@code null}
     */
    @Contract(pure = true)
    @NotNull FunctionState getFunctionState();


    /**
     * Returns the stage enum for this function.
     *
     * @return  stage enumeration, never {@code null}
     */
    @Contract(pure = true)
    @NotNull Enum<?> getStage();


    @Contract(pure = true)
    int getOrder();


    @Contract(pure = true)
    String getDescription();
  }




  /**
   * Stage function state.
   *
   * @since 0.3.0
   *
   * @see Function#getFunctionState()
   * @see StageContext#getFunctions()
   */
  enum FunctionState
  {
    /** Function has been processed. */
    PROCESSED,


    /** Function is currently executing. */
    EXECUTING,


    /** Function execution failed. */
    FAILED,


    /** Function is waiting for execution. */
    WAITING
  }
}
