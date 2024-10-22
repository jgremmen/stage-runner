package de.sayayi.lib.stagerunner;

import de.sayayi.lib.stagerunner.impl.AbstractStageRunner;
import de.sayayi.lib.stagerunner.impl.AbstractStageRunnerFactory;
import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public class DefaultStageRunnerFactory<S extends Enum<S>> extends AbstractStageRunnerFactory<S>
{
  public DefaultStageRunnerFactory(@NotNull Class<S> stageEnumType) {
    super(stageEnumType);
  }


  @Override
  public @NotNull StageRunner<S> createRunner() {
    return new AbstractStageRunner<S>(DefaultStageRunnerFactory.this) {};
  }
}
