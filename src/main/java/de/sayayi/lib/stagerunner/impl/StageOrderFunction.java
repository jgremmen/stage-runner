package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Map.Entry;


final class StageOrderFunction<S extends Enum<S>,D> implements Entry<S,StageFunction<S,D>>
{
  final @NotNull S stage;
  final int order;
  final @NotNull StageFunction<S,D> function;


  StageOrderFunction(@NotNull S stage, int order, @NotNull StageFunction<S,D> function)
  {
    this.stage = stage;
    this.order = order;
    this.function = function;
  }


  @Override
  public S getKey() {
    return stage;
  }


  @Override
  public StageFunction<S,D> getValue() {
    return function;
  }


  @Override
  public StageFunction<S,D> setValue(StageFunction<S,D> value) {
    throw new UnsupportedOperationException("setValue");
  }
}
