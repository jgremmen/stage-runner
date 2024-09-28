package de.sayayi.lib.stagerunner.exception;

import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class StageRunnerException extends RuntimeException
{
  public StageRunnerException(@NotNull String message) {
    super(message);
  }


  public StageRunnerException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }
}
