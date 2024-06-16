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
  protected final Class<S> stageEnumType;
  final StageOrderFunctionArray<S> functionArray;


  protected AbstractStageRunnerFactory(@NotNull Class<S> stageEnumType)
  {
    this.stageEnumType = stageEnumType;

    functionArray = new StageOrderFunctionArray<>();
  }


  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function) {
    functionArray.add(new StageOrderFunction<>(stage, description, order, function));
  }
}
