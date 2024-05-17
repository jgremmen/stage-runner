package de.sayayi.lib.stagerunner;

import de.sayayi.lib.stagerunner.impl.AbstractStageRunner;
import de.sayayi.lib.stagerunner.impl.AbstractStageRunnerFactory;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.*;

import static de.sayayi.lib.stagerunner.TestStage.*;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Jeroen Gremmen
 */
@DisplayName("Stage runner")
class StageRunnerTest
{
  @Test
  @DisplayName("No stage functions")
  void noStageFunctions()
  {
    val factory = new MyStageRunnerFactory();
    val runner = factory.createRunner();

    assertTrue(runner.run(emptyMap()));
    assertEquals(emptyList(), runner.log);
  }


  @Test
  @DisplayName("Pre/post callback hooks")
  void callbackHooks()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx -> {});
    factory.addStageFunction(START, ctx -> {});
    factory.addStageFunction(END, ctx -> {});
    factory.addStageFunction(PROCESS, ctx -> {});

    val runner = factory.createRunner();

    assertTrue(runner.run(emptyMap()));
    assertEquals(
        asList(">START", ">fn", "<fn", "<START", ">PROCESS", ">fn", "<fn", ">fn", "<fn", "<PROCESS", ">END", ">fn", "<fn", "<END"),
        runner.log);
  }


  @Test
  @DisplayName("Current stage reported by stage context")
  void currentStage()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx -> assertEquals(PROCESS, ctx.getCurrentStage()));
    factory.addStageFunction(START, ctx -> assertEquals(START, ctx.getCurrentStage()));
    factory.addStageFunction(END, ctx -> assertEquals(END, ctx.getCurrentStage()));
    factory.addStageFunction(PROCESS, ctx -> assertEquals(PROCESS, ctx.getCurrentStage()));

    factory
        .createRunner()
        .run(emptyMap());
  }


  @Test
  @DisplayName("Processed stages reported by stage context")
  void processedStages()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(START), ctx.getProcessedStages()));
    factory.addStageFunction(START, ctx ->
        assertEquals(emptySet(), ctx.getProcessedStages()));
    factory.addStageFunction(END, ctx ->
        assertEquals(EnumSet.of(START, PROCESS), ctx.getProcessedStages()));
    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(START), ctx.getProcessedStages()));

    factory
        .createRunner()
        .run(emptyMap());
  }


  @Test
  @DisplayName("Remaining stages reported by stage context")
  void remainingStages()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(END), ctx.getRemainingStages()));
    factory.addStageFunction(START, ctx ->
        assertEquals(EnumSet.of(PROCESS, END), ctx.getRemainingStages()));
    factory.addStageFunction(END, ctx ->
        assertEquals(emptySet(), ctx.getRemainingStages()));
    factory.addStageFunction(PROCESS, ctx ->
        assertEquals(singleton(END), ctx.getRemainingStages()));

    factory
        .createRunner()
        .run(emptyMap());
  }


  @Test
  @DisplayName("Data reported by stage context")
  void data()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, ctx -> assertEquals(456, ctx.<Integer>getData("A")));
    factory.addStageFunction(END, ctx -> assertEquals(Boolean.FALSE, ctx.<Boolean>getData("B")));

    val data = new HashMap<String,Object>();
    data.put("A", 456);
    data.put("B", false);

    factory
        .createRunner()
        .run(data);
  }


  @Test
  @DisplayName("Add stage function dynamically")
  void addFunctionDynamically()
  {
    val factory = new MyStageRunnerFactory();

    factory.addStageFunction(START, "S", ctx ->
        ctx.addStageFunction(PROCESS, "P", c -> {}));
    factory.addStageFunction(END, "E", ctx -> {});

    val runner = factory.createRunner();

    assertTrue(runner.run(emptyMap()));
    assertEquals(
        asList(">START", ">fn(S)", "<fn", "<START", ">PROCESS", ">fn(P)", "<fn", "<PROCESS", ">END", ">fn(E)", "<fn", "<END"),
        runner.log);
  }




  private static class MyStageRunnerFactory extends AbstractStageRunnerFactory<TestStage>
  {
    private MyStageRunnerFactory() {
      super(TestStage.class);
    }


    @Override
    public @NotNull MyStageRunner createRunner() {
      return new MyStageRunner(MyStageRunnerFactory.this);
    }
  }




  private static class MyStageRunner
      extends AbstractStageRunner<TestStage>
      implements StageRunnerCallback<TestStage>
  {
    private final List<String> log = new ArrayList<>();


    private MyStageRunner(@NotNull AbstractStageRunnerFactory<TestStage> stageRunnerFactory) {
      super(stageRunnerFactory);
    }


    @Override
    public boolean run(@NotNull Map<String, Object> data) {
      return super.run(data, this);
    }


    @Override
    public void preStageCallback(@NotNull StageContext<TestStage> stageContext) {
      log.add(">" + stageContext.getCurrentStage());
    }


    @Override
    public void postStageCallback(@NotNull StageContext<TestStage> stageContext, @NotNull TestStage stage) {
      log.add("<" + stage);
    }

    @Override
    public void preStageFunctionCallback(@NotNull StageContext<TestStage> stageContext, String description) {
      log.add(">fn" + (description == null ? "" : "(" + description + ')'));
    }


    @Override
    public void postStageFunctionCallback(@NotNull StageContext<TestStage> stageContext) {
      log.add("<fn");
    }


    @Override
    @SneakyThrows
    public void stageExceptionHandler(@NotNull StageContext<TestStage> context, @NotNull Throwable exception)
    {
      if (exception instanceof AssertionFailedError)
        throw exception;

      StageRunnerCallback.super.stageExceptionHandler(context, exception);
    }
  }
}
