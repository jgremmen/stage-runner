package de.sayayi.lib.stagerunner.impl;

import de.sayayi.lib.stagerunner.StageFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map.Entry;


public interface StageContextAccessor<S extends Enum<S>>
{
  <D> @NotNull Iterator<Entry<S,StageFunction<S,D>>> stageIterator();
}
