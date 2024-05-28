package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import static java.lang.reflect.Modifier.isStatic;
import static org.springframework.util.ReflectionUtils.invokeMethod;


public final class StageFunctionAdapter<S extends Enum<S>> implements StageFunction<S>
{
  private final ConversionService conversionService;
  private final Object target;
  private final Method stageFunction;

  private Function<StageContext<S>,Object>[] parameterFunctions;


  public StageFunctionAdapter(@NotNull ConversionService conversionService,
                              @NotNull Object target, @NotNull Method stageFunction)
  {
    this.conversionService = conversionService;
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


  @Contract(pure = true)
  public @NotNull Object parameterStageContext(@NotNull StageContext<?> stageContext) {
    return stageContext;
  }


  @Contract(pure = true)
  public Object parameterStageData(@NotNull StageContext<?> stageContext, @NotNull String dataName) {
    return stageContext.getData(dataName);
  }


  @Contract(pure = true)
  public Object parameterStageData(@NotNull StageContext<?> stageContext, @NotNull String dataName,
                                   @NotNull TypeDescriptor parameterType)
  {
    final Object data = stageContext.getData(dataName);

    return conversionService.convert(data, TypeDescriptor.forObject(data), parameterType);
  }


  public void setParameterFunctions(@NotNull Function<StageContext<S>,Object>[] parameterFunctions) {
    this.parameterFunctions = parameterFunctions;
  }
}
