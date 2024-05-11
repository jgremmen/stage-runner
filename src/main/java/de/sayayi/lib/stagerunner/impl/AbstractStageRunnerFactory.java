package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageConfigurer;
import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


public abstract class AbstractStageRunnerFactory<S extends Enum<S>,D,C extends StageContext<S,D>>
    implements StageRunnerFactory<S,D>, StageConfigurer<S,D>
{
  final Class<S> stageEnumType;
  final StageOrderFunctionArray<S,D> functions;


  protected AbstractStageRunnerFactory(@NotNull Class<S> stageEnumType)
  {
    this.stageEnumType = stageEnumType;

    functions = new StageOrderFunctionArray<>();
  }


  @Override
  public void addStageFunction(@NotNull S stage, @NotNull StageFunction<S, D> function, int order) {
    functions.add(new StageOrderFunction<>(stage, order, function));
  }


  @Contract(value = "_ -> new", pure = true)
  protected abstract @NotNull C createContext(D data);


  @Contract(pure = true)
  protected abstract @NotNull StageContextAccessor<S> createContextAccessor(@NotNull C context);


  protected void preStageCallback(@NotNull C context) {
  }


  protected void preStageFunctionCallback(@NotNull C context) {
  }


  protected void postStageFunctionCallback(@NotNull C context) {
  }


  protected void postStageCallback(@NotNull C context) {}


  protected void stageExceptionHandler(@NotNull C context, @NotNull Throwable exception) {
    context.abort();
  }
}
