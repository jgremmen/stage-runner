package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@FunctionalInterface
public interface StageFunctionBuilder
{
  @Contract(pure = true)
  <S extends Enum<S>> @NotNull StageFunction<S> createStageFunction(
      @NotNull StageFunctionAnnotation stageFunctionAnnotation,
      @NotNull Map<String,ResolvableType> dataNameTypeMap,
      @NotNull Method stageFunction,
      @NotNull Object bean) throws StageRunnerConfigurationException;
}
