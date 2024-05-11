package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunner;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map.Entry;


public abstract class AbstractStageRunner<S extends Enum<S>,D,C extends StageContext<S,D>>
    implements StageRunner<S,D>
{
  private final AbstractStageRunnerFactory<S,D,C> stageRunnerFactory;


  protected AbstractStageRunner(AbstractStageRunnerFactory<S,D,C> stageRunnerFactory) {
    this.stageRunnerFactory = stageRunnerFactory;
  }


  @Override
  public boolean run(D data)
  {
    final C stageContext = stageRunnerFactory.createContext(data);

    switch(stageContext.getState())
    {
      case IDLE:
        run(stageContext);
        break;

      case RUNNING:
        throw new IllegalStateException("stage runner is already running");
    }

    return !stageContext.isAborted();
  }


  private void run(@NotNull C stageContext)
  {
    final StageContextAccessor<S> stageContextAccessor =
        stageRunnerFactory.createContextAccessor(stageContext);
    final Iterator<Entry<S,StageFunction<S,D>>> iterator = stageContextAccessor.stageIterator();

    while(!stageContext.isAborted() && iterator.hasNext())
    {
      final Entry<S,StageFunction<S,D>> stageFunctionEntry = iterator.next();

      try {
        stageFunctionEntry.getValue().process(stageContext);
      } catch(Throwable ex) {
        stageContext.stageExceptionHandler(stageContext, ex);
      }
    }
  }
}
