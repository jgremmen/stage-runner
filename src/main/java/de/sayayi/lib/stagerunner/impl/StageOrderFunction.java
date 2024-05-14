package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageFunction;
import org.jetbrains.annotations.NotNull;

import static de.sayayi.lib.stagerunner.StageConfigurer.DEFAULT_ORDER;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
final class StageOrderFunction<S extends Enum<S>>
{
  final @NotNull S stage;
  final int order;
  final @NotNull StageFunction<S> function;


  StageOrderFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    this(stage, DEFAULT_ORDER, function);
  }


  StageOrderFunction(@NotNull S stage, int order, @NotNull StageFunction<S> function)
  {
    this.stage = stage;
    this.order = order;
    this.function = function;
  }


  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (!(o instanceof StageOrderFunction))
      return false;

    final StageOrderFunction<?> that = (StageOrderFunction<?>) o;

    return order == that.order && stage == that.stage && function.equals(that.function);
  }


  @Override
  public int hashCode() {
    return (stage.hashCode() * 31 + order) * 31 + function.hashCode();
  }


  @Override
  public String toString()
  {
    return "StageOrderFunction(stage=" + stage + ",order=" + order + ",function=" +
        Integer.toString(function.hashCode(), 16) + ')';
  }
}
