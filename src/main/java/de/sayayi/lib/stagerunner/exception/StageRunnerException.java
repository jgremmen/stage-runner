/*
 * Copyright 2024 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.stagerunner.exception;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;


/**
 * Base class for all exceptions thrown by the stage runner library.
 * <p>
 * It is thrown when a stage runner is used incorrectly and, by the
 * {@link de.sayayi.lib.stagerunner.StageRunnerCallback#defaultCallback() default callback}, to wrap an exception thrown
 * by a stage function.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class StageRunnerException extends RuntimeException
{
  @Serial
  private static final long serialVersionUID = -2868347670902944275L;


  /**
   * Constructs a new stage runner exception with the specified detail message.
   * The cause is not initialized, and may subsequently be initialized by a call to {@link #initCause}.
   *
   * @param message  the detail message, not {@code null}. The detail message is saved for later retrieval by the
   *                 {@link #getMessage()} method.
   */
  public StageRunnerException(@NotNull String message) {
    super(message);
  }


  /**
   * Constructs a new stage runner exception with the specified detail message and cause.
   * <p>
   * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this runtime
   * exception's detail message.
   *
   * @param message  the detail message, not {@code null}. The detail message is saved for later retrieval by the
   *                 {@link #getMessage()} method.
   * @param cause    the cause which is saved for later retrieval by the {@link #getCause()} method. A {@code null}
   *                 value is permitted, and indicates that the cause is nonexistent or unknown.
   */
  public StageRunnerException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }
}
