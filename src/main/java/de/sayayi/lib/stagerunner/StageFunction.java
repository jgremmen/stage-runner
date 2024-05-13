package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
@FunctionalInterface
public interface StageFunction<S extends Enum<S>>
{
  void process(@NotNull StageContext<S> stageContext);
}
