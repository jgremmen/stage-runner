package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 * @param <D>  Data type
 */
public interface StageConfigurer<S extends Enum<S>,D>
{
  int DEFAULT_ORDER = 1000;


  default void addStageFunction(@NotNull S stage, @NotNull StageFunction<S,D> function) {
    addStageFunction(stage, function, DEFAULT_ORDER);
  }


  void addStageFunction(@NotNull S stage, @NotNull StageFunction<S,D> function, int order);
}
