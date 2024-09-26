package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.NamedStageConfigurer;
import de.sayayi.lib.stagerunner.StageConfigurer;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public abstract class AbstractStageRunnerFactory<S extends Enum<S>>
    implements StageRunnerFactory<S>, StageConfigurer<S>, NamedStageConfigurer<S>
{
  protected final Class<S> stageEnumType;
  final StageOrderFunctionArray<S> functionArray;
  final Map<String,StageOrderFunction<S>> namedStageFunctions;


  protected AbstractStageRunnerFactory(@NotNull Class<S> stageEnumType)
  {
    this.stageEnumType = stageEnumType;

    functionArray = new StageOrderFunctionArray<>();
    namedStageFunctions = new HashMap<>();
  }


  @Override
  public void addStageFunction(@NotNull S stage, int order, String description, @NotNull StageFunction<S> function) {
    functionArray.add(new StageOrderFunction<>(stage, description, order, function));
  }


  @Override
  public void namedStageFunction(@NotNull String name, @NotNull S stage, int order, String description,
                                 @NotNull StageFunction<S> function)
  {
    if (namedStageFunctions.containsKey(name))
      throw new IllegalArgumentException("name '" + name + "' must be unique for this stage runner factory");

    namedStageFunctions.put(name, new StageOrderFunction<>(stage, description, order, function));
  }
}
