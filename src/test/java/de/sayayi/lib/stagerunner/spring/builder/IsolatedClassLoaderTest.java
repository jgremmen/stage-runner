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
package de.sayayi.lib.stagerunner.spring.builder;

import de.sayayi.lib.stagerunner.TestStage;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.StageDef;
import de.sayayi.lib.stagerunner.spring.StageFunctionAnnotation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests that a class loader which cannot load the stage runner library is reported as configuration error when a class
 * is generated for a bean or stage runner interface loaded by that class loader.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Generated classes in an isolated class loader")
class IsolatedClassLoaderTest
{
  @Test
  @DisplayName("Stage function for a bean in an isolated class loader fails with a configuration exception")
  void stageFunction() throws ReflectiveOperationException
  {
    final var beanType = new IsolatedClassLoader().loadClass(IsolatedBean.class.getName());
    final var builder = new StageFunctionBuilderImpl(new DefaultConversionService());

    final var ex = assertThrows(StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(StageFunctionAnnotation.buildFrom(StageDef.class), Map.of(),
            beanType.getMethod("run"), beanType.getConstructor().newInstance()));

    assertInstanceOf(LinkageError.class, ex.getCause());
    assertTrue(ex.getMessage().contains("class loader"), ex.getMessage());
  }


  @Test
  @DisplayName("Proxy for a stage runner interface in an isolated class loader fails with a configuration exception")
  void proxy() throws ReflectiveOperationException
  {
    final var interfaceType = new IsolatedClassLoader().loadClass(IsolatedRunner.class.getName());
    final var builder = new StageRunnerProxyBuilderImpl(false);

    final var ex = assertThrows(StageRunnerConfigurationException.class,
        () -> builder.createProxy(TestStage.class, interfaceType, interfaceType.getMethod("run"), List.of(),
            new DefaultStageRunnerFactory<>(TestStage.class)));

    assertInstanceOf(LinkageError.class, ex.getCause());
    assertTrue(ex.getMessage().contains("class loader"), ex.getMessage());
  }




  public static class IsolatedBean
  {
    public void run() {
    }
  }




  public interface IsolatedRunner {
    void run();
  }




  /**
   * Class loader which defines the isolated test types itself and delegates everything else to the platform class
   * loader, so neither the stage runner library nor Spring is visible to it.
   */
  private static final class IsolatedClassLoader extends ClassLoader
  {
    private IsolatedClassLoader() {
      super("isolated", getPlatformClassLoader());
    }


    @Override
    protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException
    {
      if (!name.equals(IsolatedBean.class.getName()) && !name.equals(IsolatedRunner.class.getName()))
        throw new ClassNotFoundException(name);

      try(var in = IsolatedClassLoaderTest.class.getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
        if (in == null)
          throw new ClassNotFoundException(name);

        final var bytes = in.readAllBytes();
        return defineClass(name, bytes, 0, bytes.length);
      } catch(IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }
}
