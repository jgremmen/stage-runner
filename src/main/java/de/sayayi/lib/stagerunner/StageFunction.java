package de.sayayi.lib.stagerunner;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 * @param <D>  Data type
 */
@FunctionalInterface
public interface StageFunction<S extends Enum<S>,D>
{
  <C extends StageContext<S,D>> void process(@NotNull C stageContext);




  @Retention(RUNTIME)
  @Target(PARAMETER)
  @interface Data {
  }
}
