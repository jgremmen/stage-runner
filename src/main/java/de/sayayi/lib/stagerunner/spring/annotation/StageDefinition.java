package de.sayayi.lib.stagerunner.spring.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public interface StageDefinition
{
  /**
   * Marks the annotation method which returns the name.
   * The return value must be of type {@code String}.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Name {
  }


  /**
   * Marks the annotation method which returns the stage.
   * The return value must be an enumeration.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Stage {
  }


  /**
   * Marks the annotation method which returns the stage function order.
   * The return value must be either an {@code int} or {@code short}.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Order {
  }


  /**
   * Marks the annotation method which returns the stage function description.
   * The return value must be of type {@code String}.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Description {
  }
}
