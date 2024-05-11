package de.sayayi.lib.stagerunner;


/**
 * @author Jeroen Gremmen
 *
 * @param <S>  Stage enum type
 * @param <D>  Data type
 */
public interface StageRunner<S extends Enum<S>,D>
{
  boolean run(D data);
}
