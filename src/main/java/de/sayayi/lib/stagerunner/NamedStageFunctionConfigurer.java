package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;

import static de.sayayi.lib.stagerunner.StageConfigurer.DEFAULT_ORDER;


/**
 * The named stage configurer provides methods to add new stage functions, which can be enabled by name.
 *
 * @param <S>  Stage enum type
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface NamedStageFunctionConfigurer<S extends Enum<S>>
{
  /**
   * Add a named stage function to {@code stage}. The function will be inserted appropriately using the
   * {@link StageConfigurer#DEFAULT_ORDER}.
   *
   * @param name      stage function name, not empty or {@code null}
   * @param stage     stage, not {@code null}
   * @param function  stage function, not {@code null}
   */
  default void namedStageFunction(@NotNull String name, @NotNull S stage, @NotNull StageFunction<S> function) {
    namedStageFunction(name, stage, DEFAULT_ORDER, null, function);
  }


  /**
   * Add a named stage function to {@code stage}. The function will be inserted appropriately using the
   * {@link StageConfigurer#DEFAULT_ORDER}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param name         stage function name, not empty or {@code null}
   * @param stage        stage, not {@code null}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   */
  default void namedStageFunction(@NotNull String name, @NotNull S stage, String description,
                                  @NotNull StageFunction<S> function) {
    namedStageFunction(name, stage, DEFAULT_ORDER, description, function);
  }


  /**
   * Add a named stage function to {@code stage}. The function will be inserted appropriately using the given
   * {@code order}.
   *
   * @param name      stage function name, not empty or {@code null}
   * @param stage     stage, not {@code null}
   * @param order     function order within the given {@code stage}
   * @param function  stage function, not {@code null}
   */
  default void namedStageFunction(@NotNull String name, @NotNull S stage, int order,
                                  @NotNull StageFunction<S> function) {
    namedStageFunction(name, stage, order, null, function);
  }


  /**
   * Add a named stage function to {@code stage}. The function will be inserted appropriately using the given
   * {@code order}.
   * <p>
   * The description is optional and will be passed to the
   * {@link StageRunnerCallback#preStageFunctionCallback(StageContext, String) preStageFunctionCallback} callback
   * function when running through all stage functions.
   *
   * @param name         stage function name, not empty or {@code null}
   * @param stage        stage, not {@code null}
   * @param order        function order within the given {@code stage}
   * @param description  stage function description or {@code null} for no description
   * @param function     stage function, not {@code null}
   *                     
   * @see StageRunnerCallback#preStageFunctionCallback(StageContext, String) 
   */
  void namedStageFunction(@NotNull String name, @NotNull S stage, int order, String description,
                          @NotNull StageFunction<S> function);
}
