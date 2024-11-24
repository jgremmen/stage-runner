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
 * This interface contains a collection of annotations used to build your stage type specific method annotation.
 * <p>
 * E.g.
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
 *   int priority() default StageContext.DEFAULT_ORDER;
 *
 *   &#x40;StageDefinition.Description
 *   String comment();
 * }
 * </pre>
 *
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
