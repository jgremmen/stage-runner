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
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;
import de.sayayi.lib.stagerunner.spring.StageDef;
import de.sayayi.lib.stagerunner.spring.StageFunctionAnnotation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests that the class cache of {@link StageFunctionBuilderImpl} distinguishes bean classes with the same name loaded
 * by different class loaders.
 *
 * @author Jeroen Gremmen
 * @since 0.4.1
 */
@DisplayName("Generated classes for same-named beans in sibling class loaders")
class SameNameClassLoaderTest
{
  @Test
  @DisplayName("Each bean class gets its own generated stage function class")
  void sameNameInSiblingClassLoaders() throws ReflectiveOperationException
  {
    final var builder = new StageFunctionBuilderImpl(new DefaultConversionService());
    final var annotation = StageFunctionAnnotation.buildFrom(StageDef.class);
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);
    final var beanType1 = new SiblingClassLoader("sibling-1").loadClass(SiblingBean.class.getName());
    final var beanType2 = new SiblingClassLoader("sibling-2").loadClass(SiblingBean.class.getName());
    final var bean1 = beanType1.getConstructor().newInstance();
    final var bean2 = beanType2.getConstructor().newInstance();

    assertNotSame(beanType1, beanType2);

    final var function1 =
        builder.<TestStage>createStageFunction(annotation, Map.of(), beanType1.getMethod("run"), bean1);
    final var function2 =
        builder.<TestStage>createStageFunction(annotation, Map.of(), beanType2.getMethod("run"), bean2);

    assertNotSame(function1.getClass(), function2.getClass());
    assertSame(beanType1.getClassLoader(), function1.getClass().getClassLoader());
    assertSame(beanType2.getClassLoader(), function2.getClass().getClassLoader());

    factory.addStageFunction(TestStage.INIT, function1);
    factory.addStageFunction(TestStage.INIT, function2);

    assertTrue(factory.createRunner().run(Map.of()));
    assertEquals(1, beanType1.getField("calls").getInt(bean1));
    assertEquals(1, beanType2.getField("calls").getInt(bean2));
  }




  public static class SiblingBean
  {
    public int calls;


    public void run() {
      calls++;
    }
  }




  /**
   * Class loader which defines {@link SiblingBean} itself and delegates everything else to the test class loader, so
   * the stage runner library is visible to it.
   */
  private static final class SiblingClassLoader extends ClassLoader
  {
    private SiblingClassLoader(@NotNull String name) {
      super(name, SameNameClassLoaderTest.class.getClassLoader());
    }


    @Override
    protected Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException
    {
      if (!name.equals(SiblingBean.class.getName()))
        return super.loadClass(name, resolve);

      synchronized(getClassLoadingLock(name)) {
        var type = findLoadedClass(name);
        if (type == null)
        {
          try(var in = SameNameClassLoaderTest.class
              .getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
            if (in == null)
              throw new ClassNotFoundException(name);

            final var bytes = in.readAllBytes();
            type = defineClass(name, bytes, 0, bytes.length);
          } catch(IOException ex) {
            throw new UncheckedIOException(ex);
          }
        }

        return type;
      }
    }
  }
}
