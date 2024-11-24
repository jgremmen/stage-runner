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
package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.TestStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


@DisplayName("Stage function annotation")
class StageFunctionAnnotationTest
{
  @Test
  @DisplayName("Fully qualified function annotation")
  void fqAnnotation()
  {
    final StageFunctionAnnotation sfa = StageFunctionAnnotation.buildFrom(StageDef.class);

    assertEquals(StageDef.class, sfa.getAnnotationType());
    assertEquals(TestStage.class, sfa.getStageType());
    assertEquals("scope", sfa.getNameProperty());
    assertEquals("stage", sfa.getStageProperty());
    assertEquals("priority", sfa.getOrderProperty());
    assertEquals("comment", sfa.getDescriptionProperty());
  }
}
