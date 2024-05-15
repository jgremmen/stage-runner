package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;

import java.util.Map;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
@FunctionalInterface
public interface StageRunner<S extends Enum<S>>
{
  default boolean run(@NotNull Map<String,Object> data) {
    return run(data, new StageRunnerCallback<S>() {});
  }


  boolean run(@NotNull Map<String,Object> data, @NotNull StageRunnerCallback<S> callback);
}
