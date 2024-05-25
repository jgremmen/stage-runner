package de.sayayi.lib.stagerunner.spring;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


public interface StageDefinition
{
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Stage {
  }




  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Order {
  }




  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Description {
  }
}
