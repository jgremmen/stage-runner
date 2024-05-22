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
  final String description;
  final int order;
  final @NotNull StageFunction<S> function;


  StageOrderFunction(@NotNull S stage, @NotNull StageFunction<S> function) {
    this(stage, null, DEFAULT_ORDER, function);
  }


  StageOrderFunction(@NotNull S stage, String description, int order,
                     @NotNull StageFunction<S> function)
  {
    this.stage = stage;
    this.description = description;
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
    final StringBuilder s = new StringBuilder(getClass().getSimpleName())
        .append("(stage=").append(stage).append('@').append(order);

    if (description != null && !description.isEmpty())
      s.append(",description=").append(description);

    return s.append(')').toString();
  }
}
