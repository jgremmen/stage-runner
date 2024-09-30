package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;

import java.util.Map;


/**
 * Interface representing a stage runner.
 * <p>
 * A new instance can be {@link StageRunnerFactory#createRunner() created} by the stage runner factory and can
 * be run only once.
 *
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
@FunctionalInterface
public interface StageRunner<S extends Enum<S>>
{
  /**
   * Start this stage runner instance with a default {@link StageRunnerCallback#INSTANCE callback}.
   *
   * @param data  data, which can be accessed in stage functions, not {@code null}
   *
   * @return  {@code true} if the stage runner ran successfully, {@code false} otherwise
   */
  @SuppressWarnings("unchecked")
  default boolean run(@NotNull Map<String,Object> data) {
    return run(data, (StageRunnerCallback<S>)StageRunnerCallback.INSTANCE);
  }


  /**
   * Start this stage runner instance.
   *
   * @param data      data, which can be accessed in stage functions, not {@code null}
   * @param callback  stage runner callback instance, not {@code null}
   *
   * @return  {@code true} if the stage runner ran successfully, {@code false} otherwise
   */
  boolean run(@NotNull Map<String,Object> data, @NotNull StageRunnerCallback<S> callback);
}
