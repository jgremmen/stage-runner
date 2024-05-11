package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
public interface StageRunnerFactory<S extends Enum<S>,D>
{
  @Contract(value = "-> new", pure = true)
  @NotNull StageRunner<S,D> createRunner();
}
