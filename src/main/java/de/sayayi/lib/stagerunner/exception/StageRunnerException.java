package de.sayayi.lib.stagerunner.exception;

import org.jetbrains.annotations.NotNull;


public class StageRunnerException extends RuntimeException
{
  public StageRunnerException(@NotNull String message) {
    super(message);
  }

  public StageRunnerException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }
}
