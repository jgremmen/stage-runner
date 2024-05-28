package de.sayayi.lib.stagerunner.annotation.proxy;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import static java.lang.reflect.Modifier.isStatic;
import static org.springframework.util.ReflectionUtils.invokeMethod;


final class StageFunctionAdapter<S extends Enum<S>> implements StageFunction<S>
{
  private final Object target;
  private final Method stageFunction;

  private Function<StageContext<S>,Object>[] parameterFunctions;


  StageFunctionAdapter(@NotNull Object target, @NotNull Method stageFunction)
  {
    this.target = isStatic(stageFunction.getModifiers()) ? null : target;
    this.stageFunction = stageFunction;
  }


  @Override
  public void process(@NotNull StageContext<S> stageContext)
  {
    invokeMethod(stageFunction, target, Arrays
        .stream(parameterFunctions)
        .map(f -> f.apply(stageContext))
        .toArray());
  }


  void setParameterFunctions(@NotNull Function<StageContext<S>,Object>[] parameterFunctions) {
    this.parameterFunctions = parameterFunctions;
  }
}
