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
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.support.DefaultConversionService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static net.bytebuddy.jar.asm.Opcodes.ASM9;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the binding of stage function parameters of a class compiled without {@code -parameters}, whose parameter
 * names are not available through reflection.
 *
 * @author Jeroen Gremmen
 * @since 0.4.0
 */
@DisplayName("Stage function parameters without names")
class MissingParameterNamesTest
{
  private final StageFunctionBuilderImpl builder = new StageFunctionBuilderImpl(new DefaultConversionService());


  @Test
  @DisplayName("Synthetic parameter name is not matched against the data names")
  void syntheticNameIsNotMatched() throws ReflectiveOperationException
  {
    final var beanType = new NamelessClassLoader().loadClass(NamelessBean.class.getName());
    final var bean = beanType.getConstructor().newInstance();
    final var method = beanType.getMethod("bind", String.class);
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);

    assertFalse(method.getParameters()[0].isNamePresent());

    // the synthetic name "arg0" must not select the convertible entry; the parameter is bound by type instead
    factory.addStageFunction(TestStage.INIT, builder.createStageFunction(
        StageFunctionAnnotation.buildFrom(StageDef.class),
        Map.of("arg0", ResolvableType.forClass(Integer.class), "text", ResolvableType.forClass(String.class)),
        method, bean));

    assertTrue(factory.createRunner().run(Map.of("arg0", 42, "text", "data")));
    assertEquals("data", beanType.getField("value").get(bean));
  }


  @Test
  @DisplayName("Ambiguous type error hints at the missing -parameters flag")
  void ambiguousTypeHint() throws ReflectiveOperationException
  {
    final var beanType = new NamelessClassLoader().loadClass(NamelessBean.class.getName());

    final var ex = assertThrows(StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(StageFunctionAnnotation.buildFrom(StageDef.class),
            Map.of("first", ResolvableType.forClass(String.class), "text", ResolvableType.forClass(String.class)),
            beanType.getMethod("bind", String.class), beanType.getConstructor().newInstance()));

    assertTrue(ex.getMessage().contains("compile " + NamelessBean.class.getName() + " with -parameters"),
        ex.getMessage());
  }


  @Test
  @DisplayName("Present parameter name is matched and no hint is given")
  void presentNameIsMatched() throws ReflectiveOperationException
  {
    final var bean = new NamelessBean();
    final var factory = new DefaultStageRunnerFactory<>(TestStage.class);

    factory.addStageFunction(TestStage.INIT, builder.createStageFunction(
        StageFunctionAnnotation.buildFrom(StageDef.class),
        Map.of("first", ResolvableType.forClass(String.class), "text", ResolvableType.forClass(String.class)),
        NamelessBean.class.getMethod("bind", String.class), bean));

    assertTrue(factory.createRunner().run(Map.of("first", "other", "text", "data")));
    assertEquals("data", bean.value);

    final var ex = assertThrows(StageRunnerConfigurationException.class,
        () -> builder.createStageFunction(StageFunctionAnnotation.buildFrom(StageDef.class),
            Map.of("first", ResolvableType.forClass(String.class), "second", ResolvableType.forClass(String.class)),
            NamelessBean.class.getMethod("bind", String.class), bean));

    assertFalse(ex.getMessage().contains("-parameters"), ex.getMessage());
  }




  public static class NamelessBean
  {
    public Object value;


    public void bind(String text) {
      value = text;
    }
  }




  /**
   * Class loader which defines {@link NamelessBean} itself, with the {@code MethodParameters} attribute removed from
   * its class file. This has the same effect as compiling the class without {@code -parameters}. All other classes are
   * loaded by the parent class loader.
   */
  private static final class NamelessClassLoader extends ClassLoader
  {
    private NamelessClassLoader() {
      super("nameless", MissingParameterNamesTest.class.getClassLoader());
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
      if (!name.equals(NamelessBean.class.getName()))
        return super.loadClass(name, resolve);

      synchronized(getClassLoadingLock(name))
      {
        var type = findLoadedClass(name);
        if (type == null)
        {
          final var bytes = withoutParameterNames(name);
          type = defineClass(name, bytes, 0, bytes.length);
        }

        return type;
      }
    }


    private static byte @NotNull [] withoutParameterNames(@NotNull String name)
    {
      try(var in = MissingParameterNamesTest.class
          .getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
        if (in == null)
          throw new IllegalStateException("class file for " + name + " not found");

        final var writer = new ClassWriter(0);

        new ClassReader(in.readAllBytes()).accept(new ClassVisitor(ASM9, writer) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                           String[] exceptions)
          {
            return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
              @Override
              public void visitParameter(String name, int access) {
                // drop the MethodParameters attribute entry
              }
            };
          }
        }, 0);

        return writer.toByteArray();
      } catch(IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }
}
