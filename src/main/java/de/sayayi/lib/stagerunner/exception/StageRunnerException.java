package de.sayayi.lib.stagerunner.exception;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class StageRunnerException extends RuntimeException
{
  public StageRunnerException(String message) {
    super(message);
  }


  public StageRunnerException(String message, Throwable cause) {
    super(message, cause);
  }
}
