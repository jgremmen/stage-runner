package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;


/**
 * Interface representing a processable stage function.
 *
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 */
@FunctionalInterface
public interface StageFunction<S extends Enum<S>>
{
  /**
   * Process the stage function, providing the stage context.
   *
   * @param stageContext  stage runner context, never {@code null}
   */
  void process(@NotNull StageContext<S> stageContext);
}
