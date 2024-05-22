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
  int DEFAULT_ORDER = 1000;


  default void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    addStageFunction(stage, null, function, DEFAULT_ORDER);
  }


  default void addStageFunction(@NotNull S stage, String description, @NotNull StageFunction<S> function) {
    addStageFunction(stage, description, function, DEFAULT_ORDER);
  }


  default void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function, int order) {
    addStageFunction(stage, null, function, order);
  }


  void addStageFunction(@NotNull S stage, String description, @NotNull StageFunction<S> function, int order);
}
