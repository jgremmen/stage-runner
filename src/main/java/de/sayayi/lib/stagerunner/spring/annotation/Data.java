package de.sayayi.lib.stagerunner.spring.annotation;

import de.sayayi.lib.stagerunner.StageRunner;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * This annotation is used for associating method parameters - either stage function parameters or runner interface
 * functional method parameters - with the data passed to {@link StageRunner#run(Map)}.
 * <p>
 * If this annotation is missing, the parameter name is used.
 *
 * @see StageDefinition
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Data
{
  /**
   * Name of the data parameter.
   */
  String value();
}
