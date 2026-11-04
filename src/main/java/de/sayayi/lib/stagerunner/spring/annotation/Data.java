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
 * The annotation can be used on parameters of the functional method of a stage runner interface as well as on stage
 * function parameters.
 *
 * <h2>Stage runner interface parameters</h2>
 * Every parameter, except a {@link de.sayayi.lib.stagerunner.StageRunnerCallback StageRunnerCallback}, is passed to the
 * stage runner as a data entry. The entry is named by this annotation or, if it is missing, by the parameter name. The
 * annotation must specify a name and is not allowed on the callback parameter.
 *
 * <h2>Stage function parameters</h2>
 * Each parameter is bound by the first matching rule:
 * <ol>
 *   <li>
 *     If the parameter is annotated with a name, it receives the data entry with that name. The entry must exist and
 *     its type must be assignable or convertible to the parameter type; matching by type is not attempted.
 *   </li>
 *   <li>
 *     If the parameter is not annotated and its type is {@link de.sayayi.lib.stagerunner.StageContext StageContext}, it
 *     receives the stage context.
 *   </li>
 *   <li>
 *     Otherwise it receives the data entry named like the parameter, whose type must be assignable or convertible to
 *     the parameter type. If no entry with that name exists, the entry is matched by type. The strongest match is
 *     chosen; equally strong matches of different entries are rejected as ambiguous.
 *   </li>
 * </ol>
 * Matching by parameter name requires the parameter names to be present in the class file, i.e. the class must be
 * compiled with {@code -parameters}. Without them, the third rule matches by type only. The parameter names of the
 * stage runner interface are required as well, unless every data parameter is annotated with a name. An annotation
 * without name is only allowed on a parameter which can be assigned the stage context, like {@code StageContext},
 * {@code StageFunctionConfigurer} or {@code Object}. It prevents the parameter from receiving the stage context and
 * continues with the last rule.
 * <p>
 * All violations are detected at startup and rejected with a
 * {@link de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException StageRunnerConfigurationException}.
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
   * @return  data name, or an empty string to prevent a stage function parameter from being assigned the stage
   *          context (see class documentation)
   */
  @AliasFor("name")
  String value() default "";


  /**
   * Name of the data entry. Alias for {@link #value()}.
   *
   * @return  data name, or an empty string to prevent a stage function parameter from being assigned the stage
   *          context (see class documentation)
   */
  @AliasFor("value")
  String name() default "";
}
