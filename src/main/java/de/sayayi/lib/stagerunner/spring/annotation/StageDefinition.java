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
package de.sayayi.lib.stagerunner.spring.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Collection of meta annotations used to build an application specific stage function annotation.
 * <p>
 * The properties of the stage function annotation are marked with these meta annotations. A {@link Stage @Stage}
 * property is mandatory; {@link Name @Name}, {@link Order @Order} and {@link Description @Description} are optional.
 * For example:
 * <pre>
 * &#x40;Target(METHOD)
 * &#x40;Retention(RUNTIME)
 * public &#x40;interface MyStageDef
 * {
 *   &#x40;StageDefinition.Name
 *   String scope() default "";
 *
 *   &#x40;StageDefinition.Stage
 *   MyStage stage();
 *
 *   &#x40;StageDefinition.Order
 *   int priority() default StageFunctionConfigurer.DEFAULT_ORDER;
 *
 *   &#x40;StageDefinition.Description
 *   String comment();
 * }
 * </pre>
 * <p>
 * A stage function is then decorated with this annotation:
 * <pre>
 * &#x40;MyStageDef(stage = MyStage.INIT, priority = 100, comment = "initialize")
 * public void init(StageContext stageContext) {
 *   ...
 * }
 * </pre>
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public interface StageDefinition
{
  /**
   * Marks the annotation property which provides the stage function name. The property type must be {@code String}.
   * <p>
   * A stage function with a non empty name is registered as a named stage function, which must be enabled explicitly
   * for an execution.
   *
   * @see de.sayayi.lib.stagerunner.StageFunctionConfigurer.Named
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Name {
  }


  /**
   * Marks the annotation property which provides the stage. The property type must be the stage enum type.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Stage {
  }


  /**
   * Marks the annotation property which provides the stage function order. The property type must be {@code int}.
   * <p>
   * If the stage function annotation has no order property, stage functions are registered with
   * {@link de.sayayi.lib.stagerunner.StageFunctionConfigurer#DEFAULT_ORDER the default order}.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Order {
  }


  /**
   * Marks the annotation property which provides the stage function description. The property type must be
   * {@code String}.
   * <p>
   * The description is passed to
   * {@link de.sayayi.lib.stagerunner.StageRunnerCallback#preStageFunctionCallback preStageFunctionCallback}. If the
   * stage function annotation has no description property, stage functions are registered without description.
   */
  @Target(METHOD)
  @Retention(RUNTIME)
  @interface Description {
  }
}
