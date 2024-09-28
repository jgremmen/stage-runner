package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.spring.annotation.Data;

import java.util.List;


public interface MyRunnerInterface
{
  boolean run(String task, @Data(name = "map") List<Integer> params, int count);
}
