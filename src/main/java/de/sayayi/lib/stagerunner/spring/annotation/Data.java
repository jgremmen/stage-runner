package de.sayayi.lib.stagerunner.spring.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Data
{
  String name();

  boolean ignoreIfNotSet() default false;
}
