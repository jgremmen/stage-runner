package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.spring.annotation.Data;

import java.beans.Transient;
import java.util.List;


public interface MyRunnerInterface
{
  @Transient
  boolean run(String task, @Data(name = "map") List<Integer> params, int count);
}
