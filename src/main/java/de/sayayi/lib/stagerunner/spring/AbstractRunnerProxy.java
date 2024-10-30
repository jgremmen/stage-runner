package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public abstract class AbstractRunnerProxy<S extends Enum<S>>
{
  protected final StageRunnerFactory<S> stageRunnerFactory;
  protected final String[] dataNames;


  protected AbstractRunnerProxy(@NotNull StageRunnerFactory<S> stageRunnerFactory, String[] dataNames)
  {
    this.stageRunnerFactory = stageRunnerFactory;
    this.dataNames = dataNames;
  }


  protected boolean run(@NotNull Map<String,Object> data, StageRunnerCallback<S> callback)
  {
    final StageRunner<S> stageRunner = stageRunnerFactory.createRunner();

    data = Map.copyOf(data);

    return callback != null
        ? stageRunner.run(data, callback)
        : stageRunner.run(data);
  }
}
