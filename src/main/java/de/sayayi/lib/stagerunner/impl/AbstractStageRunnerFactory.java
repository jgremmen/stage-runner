package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageConfigurer;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public abstract class AbstractStageRunnerFactory<S extends Enum<S>>
    implements StageRunnerFactory<S>, StageConfigurer<S>
{
  final Class<S> stageEnumType;
  final StageOrderFunctionArray<S> functions;


  protected AbstractStageRunnerFactory(@NotNull Class<S> stageEnumType)
  {
    this.stageEnumType = stageEnumType;

    functions = new StageOrderFunctionArray<>();
  }


  @Override
  public void addStageFunction(@NotNull S stage, @NotNull StageFunction<S> function, int order) {
    functions.add(new StageOrderFunction<>(stage, order, function));
  }
}
