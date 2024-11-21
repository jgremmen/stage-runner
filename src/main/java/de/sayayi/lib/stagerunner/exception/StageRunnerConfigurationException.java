package de.sayayi.lib.stagerunner.exception;

import org.jetbrains.annotations.NotNull;


/**
 * Stage runner configuration exception.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class StageRunnerConfigurationException extends StageRunnerException
{
  /**
   * Constructs a new stage runner configuration exception with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a call to {@link #initCause}.
   *
   * @param  message  the detail message. The detail message is saved for later retrieval by the
   *                  {@link #getMessage()} method.
   */
  public StageRunnerConfigurationException(@NotNull String message) {
    super(message);
  }


  /**
   * Constructs a new stage runner configuration exception with the specified detail message and cause.
   * <p>
   * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in
   * this runtime exception's detail message.
   *
   * @param  message  the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
   * @param  cause  the cause which is saved for later retrieval by the {@link #getCause()} method.
   *                A <tt>null</tt> value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public StageRunnerConfigurationException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }
}
