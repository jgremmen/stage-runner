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
package de.sayayi.lib.stagerunner.spi;

import de.sayayi.lib.stagerunner.TestStage;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Jeroen Gremmen
 */
@DisplayName("Stage order function array")
class StageOrderFunctionArrayTest
{
  @Test
  @DisplayName("Sort by stage type")
  void sortByStageType()
  {
    val array = new StageOrderFunctionArray<>(new StageOrderFunctionArray<TestStage>());

    assertEquals(0, array.add(new StageOrderFunction<>(START, ctx -> {})));
    assertEquals(1, array.add(new StageOrderFunction<>(CLEANUP, ctx -> {})));
    assertEquals(0, array.add(new StageOrderFunction<>(INIT, ctx -> {})));
    assertEquals(2, array.add(new StageOrderFunction<>(PROCESS, ctx -> {})));
    assertEquals(4, array.add(new StageOrderFunction<>(END, ctx -> {})));
  }


  @Test
  @DisplayName("Sort by default order")
  void sortByDefaultOrder()
  {
    val array = new StageOrderFunctionArray<TestStage>();

    // prepare
    assertEquals(0, array.add(new StageOrderFunction<>(START, ctx -> {})));
    assertEquals(0, array.add(new StageOrderFunction<>(INIT, ctx -> {})));
    assertEquals(2, array.add(new StageOrderFunction<>(CLEANUP, ctx -> {})));

    val arrayCopy = new StageOrderFunctionArray<>(array);

    // add existing stage types
    assertEquals(2, arrayCopy.add(new StageOrderFunction<>(START, ctx -> {})));
    assertEquals(3, arrayCopy.add(new StageOrderFunction<>(START, ctx -> {})));
    assertEquals(5, arrayCopy.add(new StageOrderFunction<>(CLEANUP, ctx -> {})));
  }


  @Test
  @DisplayName("Sort by order")
  void sortByOrder()
  {
    val array = new StageOrderFunctionArray<TestStage>();

    // prepare
    assertEquals(0, array.add(new StageOrderFunction<>(PROCESS, null, 100, ctx -> {})));

    // vary order
    assertEquals(0, array.add(new StageOrderFunction<>(PROCESS, null, 90, ctx -> {})));
    assertEquals(1, array.add(new StageOrderFunction<>(PROCESS, null, 95, ctx -> {})));
    assertEquals(2, array.add(new StageOrderFunction<>(PROCESS, null, 95, ctx -> {})));
    assertEquals(4, array.add(new StageOrderFunction<>(PROCESS, null, 100, ctx -> {})));
    assertEquals(5, array.add(new StageOrderFunction<>(PROCESS, null, 200, ctx -> {})));
  }
}
