/*
 * Copyright 2026 Jeroen Gremmen
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

/**
 * Application module used for testing the stage runner library on the module path.
 * <p>
 * The test package is only opened to the modules which need reflective access: the stage runner library (for defining
 * generated classes in the package), Spring and JUnit.
 *
 * @since 0.4.0
 */
module de.sayayi.lib.stagerunner.it
{
  requires de.sayayi.lib.stagerunner;

  requires net.bytebuddy;
  requires spring.context;
  requires org.junit.jupiter.api;

  opens de.sayayi.lib.stagerunner.it to
      de.sayayi.lib.stagerunner, spring.core, spring.beans, spring.context, org.junit.platform.commons;
}
