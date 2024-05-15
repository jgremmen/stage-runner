package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public abstract class AbstractStageRunner<S extends Enum<S>>
    implements StageRunner<S>
{
  private final AbstractStageRunnerFactory<S> stageRunnerFactory;


  protected AbstractStageRunner(@NotNull AbstractStageRunnerFactory<S> stageRunnerFactory) {
    this.stageRunnerFactory = stageRunnerFactory;
  }


  @Override
  public boolean run(@NotNull Map<String,Object> data, @NotNull StageRunnerCallback<S> callback) {
    return new StageContextImpl<>(stageRunnerFactory, data).run(callback);
  }
}
