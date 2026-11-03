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
package de.sayayi.lib.stagerunner;

import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyMap;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests that a stage runner factory can be configured and run concurrently.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage runner factory concurrency")
class StageRunnerFactoryConcurrencyTest
{
  private static final int THREADS = 8;
  private static final int FUNCTIONS_PER_THREAD = 500;


  @Test
  @DisplayName("Stage functions added concurrently are all registered, while runners are executed")
  @SuppressWarnings("resource")
  void concurrentConfigurationAndExecution() throws Exception
  {
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    final var executor = newFixedThreadPool(THREADS * 2);
    final var start = new CountDownLatch(1);
    final var futures = new ArrayList<Future<?>>();

    try {
      for(var t = 0; t < THREADS; t++)
      {
        final var stage = TestStage.values()[t % TestStage.values().length];
        final var thread = t;

        // add stage functions
        futures.add(executor.submit(() -> {
          start.await();
          for(var n = 0; n < FUNCTIONS_PER_THREAD; n++)
          {
            factory.addStageFunction(stage, ctx -> {});
            factory.namedStageFunction("t" + thread + "-" + n, stage, ctx -> {});
          }
          return null;
        }));

        // run while the configuration changes; each run must see a consistent snapshot
        futures.add(executor.submit(() -> {
          start.await();
          for(var n = 0; n < FUNCTIONS_PER_THREAD / 10; n++)
            assertTrue(factory.createRunner().run(emptyMap()));
          return null;
        }));
      }

      start.countDown();

      for(var future: futures)
        future.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // all regular functions are registered
    final var regular = new AtomicInteger();
    final var named = new AtomicInteger();

    assertTrue(factory.createRunner().run(emptyMap(), new StageRunnerCallback<>() {
      @Override
      public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext,
                                           String description) {
        regular.incrementAndGet();
      }
    }));

    assertEquals(THREADS * FUNCTIONS_PER_THREAD, regular.get());

    // all named functions are registered
    factory.addStageFunction(TestStage.INIT, Integer.MIN_VALUE, ctx ->
        named.set(ctx.enableNamedStageFunctions(name -> true).size()));
    assertTrue(factory.createRunner().run(emptyMap()));
    assertEquals(THREADS * FUNCTIONS_PER_THREAD, named.get());
  }
}
