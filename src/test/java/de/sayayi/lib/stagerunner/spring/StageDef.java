package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.spring.annotation.StageDefinition;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@Target(METHOD)
@Retention(RUNTIME)
public @interface StageDef
{
  @StageDefinition.Name
  String scope() default "";

  @StageDefinition.Stage
  TestStage stage();

  @StageDefinition.Order
  short priority() default StageContext.DEFAULT_ORDER;

  @StageDefinition.Description
  String comment();
}
