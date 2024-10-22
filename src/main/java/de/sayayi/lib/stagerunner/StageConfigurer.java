package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;


/**
 * The stage configurer provides methods to add new stage functions.
 *
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
@FunctionalInterface
public interface StageConfigurer<S extends Enum<S>>
{
  /** Default stage order if no specific order is supplied. */
  int DEFAULT_ORDER = 1000;


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the {@link #DEFAULT_ORDER}.
   *
   * @param stage     stage, not {@code null}
   * @param function  stage function, not {@code null}
   *
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
   */
  default void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    addStageFunction(stage, DEFAULT_ORDER, null, function);
  }


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the {@link #DEFAULT_ORDER}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param stage        stage, not {@code null}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   *
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String)
   */
  default void addStageFunction(@NotNull S stage, String description, @NotNull StageFunction<S> function) {
    addStageFunction(stage, DEFAULT_ORDER, description, function);
  }


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the given {@code order}.
   *
   * @param stage        stage, not {@code null}
   * @param order        function order within the given {@code stage}
   * @param function     stage function, not {@code null}
   */
  default void addStageFunction(@NotNull S stage, int order, @NotNull StageFunction<S> function) {
    addStageFunction(stage, order, null, function);
  }


  /**
   * Add a stage function to {@code stage}. The function will be inserted appropriately using the given {@code order}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param stage        stage, not {@code null}
   * @param order        function order within the given {@code stage}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   *                     
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String) 
   */
  void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function);
}
