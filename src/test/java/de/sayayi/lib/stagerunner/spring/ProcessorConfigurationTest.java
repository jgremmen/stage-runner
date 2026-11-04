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
package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.spring.builder.StageFunctionBuilderImpl;
import de.sayayi.lib.stagerunner.spring.builder.StageRunnerProxyBuilderImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests that configuration setters of {@link StageRunnerFactoryProcessor}, which only affect the default builders, are
 * rejected once the corresponding builder exists.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage runner factory processor configuration")
class ProcessorConfigurationTest
{
  @Test
  @DisplayName("Conversion service is accepted before the stage function builder exists")
  void conversionServiceBeforeBuilder()
  {
    final var processor = createProcessor();

    assertDoesNotThrow(() -> processor.setConversionService(new DefaultConversionService()));
    assertInstanceOf(StageFunctionBuilderImpl.class, processor.getStageFunctionBuilder());
  }


  @Test
  @DisplayName("Conversion service is rejected after the default stage function builder has been created")
  void conversionServiceAfterDefaultBuilder()
  {
    final var processor = createProcessor();

    processor.setConversionService(new DefaultConversionService());
    processor.getStageFunctionBuilder();

    assertThrows(IllegalStateException.class,
        () -> processor.setConversionService(new DefaultConversionService()));
  }


  @Test
  @DisplayName("Conversion service is rejected after a stage function builder has been set")
  void conversionServiceAfterCustomBuilder()
  {
    final var processor = createProcessor();

    processor.setStageFunctionBuilder(new StageFunctionBuilderImpl(new DefaultConversionService()));

    assertThrows(IllegalStateException.class,
        () -> processor.setConversionService(new DefaultConversionService()));
  }


  @Test
  @DisplayName("Copy interface method annotations is accepted before the proxy builder exists")
  void copyAnnotationsBeforeBuilder()
  {
    final var processor = createProcessor();

    assertDoesNotThrow(() -> processor.setCopyInterfaceMethodAnnotations(true));
    processor.afterPropertiesSet();
    assertInstanceOf(StageRunnerProxyBuilderImpl.class, processor.getStageRunnerProxyBuilder());
  }


  @Test
  @DisplayName("Copy interface method annotations is rejected after the default proxy builder has been created")
  void copyAnnotationsAfterDefaultBuilder()
  {
    final var processor = createProcessor();

    processor.afterPropertiesSet();

    assertThrows(IllegalStateException.class, () -> processor.setCopyInterfaceMethodAnnotations(true));
  }


  @Test
  @DisplayName("Copy interface method annotations is rejected after a proxy builder has been set")
  void copyAnnotationsAfterCustomBuilder()
  {
    final var processor = createProcessor();

    processor.setStageRunnerProxyBuilder(new StageRunnerProxyBuilderImpl(true));

    assertThrows(IllegalStateException.class, () -> processor.setCopyInterfaceMethodAnnotations(true));
  }


  private static StageRunnerFactoryProcessor<MyRunnerInterface> createProcessor() {
    return new StageRunnerFactoryProcessor<>(MyRunnerInterface.class, StageDef.class);
  }
}
