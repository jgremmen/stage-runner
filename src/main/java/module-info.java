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

module de.sayayi.lib.stagerunner {

  // optional requirement for Spring
  requires static net.bytebuddy;
  requires static spring.aop;
  requires static spring.core;
  requires static spring.beans;
  requires static spring.jcl;

  // compile time requirement
  requires static org.jetbrains.annotations;

  // exports
  exports de.sayayi.lib.stagerunner;
  exports de.sayayi.lib.stagerunner.exception;
  exports de.sayayi.lib.stagerunner.spi;
  exports de.sayayi.lib.stagerunner.spring;
  exports de.sayayi.lib.stagerunner.spring.annotation;

  // provide access to Spring
  opens de.sayayi.lib.stagerunner.spring to spring.core;
}