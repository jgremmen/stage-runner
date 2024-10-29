package de.sayayi.lib.stagerunner.exception;

import org.jetbrains.annotations.NotNull;


/**
 * Stage runner exception.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class StageRunnerConfigurationException extends StageRunnerException
{
  public StageRunnerConfigurationException(@NotNull String message) {
    super(message);
  }


  public StageRunnerConfigurationException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }
}
