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

import de.sayayi.lib.stagerunner.StageRunner;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Associates a method parameter with an entry of the data passed to {@link StageRunner#run(Map)}.
 * <p>
 * The annotation can be used on stage function parameters as well as on parameters of the functional method of a stage
 * runner interface. If this annotation is missing, the parameter name is used as data name and, for stage function
 * parameters, the data entry is matched by type if no entry with that name exists.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 *
 * @see StageDefinition
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Data
{
  /**
   * Name of the data entry. Alias for {@link #name()}.
   *
   * @return  data name, or an empty string to use the parameter name
   */
  @AliasFor("name")
  String value() default "";


  /**
   * Name of the data entry. Alias for {@link #value()}.
   *
   * @return  data name, or an empty string to use the parameter name
   */
  @AliasFor("value")
  String name() default "";
}
