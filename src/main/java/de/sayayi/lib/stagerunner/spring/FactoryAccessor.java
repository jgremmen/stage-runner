package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageFunctionConfigurer;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 * @since 0.3.2
 */
@FunctionalInterface
public interface FactoryAccessor<S extends Enum<S>>
{
  @Contract(pure = true)
  @NotNull StageRunnerFactory<S> getStageRunnerFactory();


  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  default StageFunctionConfigurer<S> getStageFunctionConfigurer()
  {
    var factory = getStageRunnerFactory();
    if (factory instanceof StageFunctionConfigurer)
      return (StageFunctionConfigurer<S>)factory;

    throw new IllegalStateException("stage runner factory is not configurable");
  }


  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  default StageFunctionConfigurer.Named<S> getNamedStageFunctionConfigurer()
  {
    var factory = getStageRunnerFactory();
    if (factory instanceof StageFunctionConfigurer.Named)
      return (StageFunctionConfigurer.Named<S>)factory;

    throw new IllegalStateException("stage runner factory is not configurable");
  }
}
