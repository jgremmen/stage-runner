# Stage Runner

A lightweight Java library for executing an ordered sequence of *stage functions* — small units of work
organized around the constants of a user-defined stage `enum`.

Stage Runner lets you model a workflow as a series of well-defined stages (for example `INIT`, `START`,
`PROCESS`, `CLEANUP`, `END`), register one or more functions per stage, and run them in a predictable order.
While the runner is executing, functions can share data, inspect progress, add new work dynamically, or
abort the run altogether.

An optional Spring integration wires stage functions directly from annotated methods on Spring managed
beans.

## Features

- Type-safe stages driven by any user-defined `enum`.
- Per-stage function order, plus optional descriptions surfaced through callbacks.
- Shared data map available to every stage function through the `StageContext`.
- Named stage functions that can be enabled on demand at runtime.
- Dynamic registration of additional stage functions while the runner is executing.
- Callback hooks for pre/post stage, pre/post stage function, addition, and exception handling.
- Cooperative abort through `StageContext.abort()`.
- Ready-to-use `DefaultStageRunnerFactory`, or subclass `AbstractStageRunnerFactory` for full control.
- Optional Spring integration that binds an application-defined runner interface to annotated beans.
- Java Platform Module System (JPMS) ready — exports the module `de.sayayi.lib.stagerunner`.

## Requirements

- Java 21 or newer.
- Optional runtime dependencies (only required for the corresponding features):
  - Spring Framework 5.3+ (used by `de.sayayi.lib.stagerunner.spring`).
  - Byte Buddy 1.15+ (used to build the Spring runner proxy).

## Installation

The library is published to Maven Central under the coordinates:

- **Group id:** `de.sayayi.lib`
- **Artifact id:** `stage-runner`

### Gradle

```groovy
dependencies {
  implementation 'de.sayayi.lib:stage-runner:<version>'

  // optional — only needed for the Spring integration
  implementation 'org.springframework:spring-context'
  implementation 'net.bytebuddy:byte-buddy'
}
```

### Maven

```xml
<dependency>
  <groupId>de.sayayi.lib</groupId>
  <artifactId>stage-runner</artifactId>
  <version>${stage-runner.version}</version>
</dependency>
```

## Core concepts

| Type | Purpose |
| ---- | ------- |
| `StageFunction<S>` | Functional interface implemented by each unit of work. Receives a `StageContext`. |
| `StageFunctionConfigurer<S>` | API for registering stage functions with a stage, order, and optional description. |
| `StageFunctionConfigurer.Named<S>` | Extension for registering *named* stage functions that can be enabled at runtime. |
| `StageRunnerFactory<S>` | Creates fresh, single-use `StageRunner` instances. |
| `StageRunner<S>` | Executes the configured stage functions once, using an input data map. |
| `StageContext<S>` | Runtime view: current stage, processed/remaining stages, shared data, abort, dynamic registration. |
| `StageRunnerCallback<S>` | Optional hook interface with pre/post callbacks and an exception handler. |
| `DefaultStageRunnerFactory<S>` | Ready-to-use factory backed by `AbstractStageRunner`. |
| `AbstractStageRunnerFactory<S>` / `AbstractStageRunner<S>` | Base classes for custom factories/runners. |

Runner instances are **single-use** — obtain a new one from the factory for every execution.

## Quick start

Define a stage enum:

```java
public enum MyStage {
  INIT, START, PROCESS, CLEANUP, END
}
```

Create a factory, register stage functions, and run:

```java
import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.spi.DefaultStageRunnerFactory;

import java.util.HashMap;
import java.util.Map;

var factory = new DefaultStageRunnerFactory<>(MyStage.class);

factory.addStageFunction(MyStage.START, ctx -> System.out.println("starting"));
factory.addStageFunction(MyStage.PROCESS, "process task", ctx -> {
  String task = ctx.getData("task");
  System.out.println("processing " + task);
});
factory.addStageFunction(MyStage.END, ctx -> System.out.println("done"));

Map<String, Object> data = new HashMap<>();
data.put("task", "import-users");

StageRunner<MyStage> runner = factory.createRunner();
boolean ok = runner.run(data);
```

Stage functions registered against the same stage are executed in their configured `order`
(defaulting to `StageFunctionConfigurer.DEFAULT_ORDER = 1000`).

### Using the stage context

Inside a stage function you have full access to the ongoing run:

```java
factory.addStageFunction(MyStage.PROCESS, ctx -> {
  MyStage current   = ctx.getCurrentStage();
  Set<MyStage> done = ctx.getProcessedStages();
  Set<MyStage> todo = ctx.getRemainingStages();

  Integer count = ctx.getData("count");
  if (count == null || count <= 0)
    ctx.abort();

  // dynamically add another stage function for a later stage
  ctx.addStageFunction(MyStage.CLEANUP, "close resources", c -> closeResources(c));
});
```

### Named stage functions

Named functions are pre-registered but only executed when explicitly enabled during a run — useful for
optional or feature-flagged behavior.

```java
factory.namedStageFunction("audit", MyStage.END, ctx -> writeAuditLog(ctx));

factory.addStageFunction(MyStage.START, ctx -> {
  if (Boolean.TRUE.equals(ctx.getData("auditing")))
    ctx.enableNamedStageFunction("audit");
});
```

You can also enable a group of functions by name predicate:

```java
ctx.enableNamedStageFunctions(name -> name.startsWith("metrics."));
```

### Callbacks

Supply a `StageRunnerCallback` to observe or influence execution:

```java
runner.run(data, new StageRunnerCallback<MyStage>() {
  @Override public void preStageCallback(StageContext<MyStage> ctx) {
    log.info("entering stage {}", ctx.getCurrentStage());
  }

  @Override public void preStageFunctionCallback(StageContext<MyStage> ctx, String description) {
    if (description != null)
      log.info("  running: {}", description);
  }

  @Override public void stageExceptionHandler(StageContext<MyStage> ctx, Throwable exception) {
    log.warn("stage {} failed", ctx.getCurrentStage(), exception);
    // rethrow to abort, or swallow to continue with remaining functions
    StageRunnerCallback.super.stageExceptionHandler(ctx, exception);
  }
});
```

The default callback wraps exceptions in `StageRunnerException` and aborts the run.

### Custom factories

For advanced use cases, subclass `AbstractStageRunnerFactory` and return a custom `AbstractStageRunner`
from `createRunner()`. See `DefaultStageRunnerFactory` for the minimal template.

## Spring integration

The `de.sayayi.lib.stagerunner.spring` package binds a user-defined runner interface to Spring managed
beans. Stage functions are declared as methods carrying a stage-function annotation you define using the
building blocks in `de.sayayi.lib.stagerunner.spring.annotation.StageDefinition`.

### 1. Define your stage-function annotation

```java
import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.spring.annotation.StageDefinition;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(METHOD)
@Retention(RUNTIME)
public @interface StageDef 
{
  @StageDefinition.Name
  String scope() default "";

  @StageDefinition.Stage
  MyStage stage();

  @StageDefinition.Order
  int priority() default StageContext.DEFAULT_ORDER;

  @StageDefinition.Description
  String comment() default "";
}
```

### 2. Define a runner interface

The interface's single non-`default`, non-`@Transient` method describes the input parameters that will be
passed as the shared data map. Use `@Data` to override a parameter's data-map name.

```java
import de.sayayi.lib.stagerunner.spring.annotation.Data;

import java.beans.Transient;
import java.util.List;

public interface MyRunner 
{
  @Transient
  boolean run(String task, @Data("map") List<Integer> params, int count);
}
```

### 3. Annotate stage functions on Spring beans

```java
@Component
public class MyBean 
{
  @StageDef(stage = MyStage.INIT, comment = "Initialize")
  public void init(StageContext<MyStage> context, List<Long> map) { ... }

  @StageDef(stage = MyStage.PROCESS, comment = "Process task")
  public void task(String task, @Data(name = "count") int n) { ... }
}
```

Stage function method parameters may include a `StageContext` and any values from the data map; matching is
performed by parameter name (or by `@Data` value) and by type via Spring's `ConversionService`.

### 4. Register the factory processor

```java
@Configuration
public class StageConfiguration 
{
  @Bean
  StageRunnerFactoryProcessor<MyRunner> stageRunnerProcessor() {
    return new StageRunnerFactoryProcessor<>(MyRunner.class, StageDef.class);
  }
}
```

At startup the processor:

- scans Spring managed beans for methods annotated with `@StageDef`,
- turns each matching method into a `StageFunction` (using `StageFunctionBuilder`),
- optionally filters candidates through a `StageFunctionFilter`,
- creates a proxy implementing `MyRunner` (using `StageRunnerProxyBuilder`), and
- registers it as a singleton bean you can inject anywhere.

```java
@Autowired MyRunner myRunner;

myRunner.run("import-users", List.of(1, 67, -4), 56);
```

## Modules and packages

- `de.sayayi.lib.stagerunner` — public API: `StageRunner`, `StageRunnerFactory`, `StageContext`,
  `StageFunction`, `StageFunctionConfigurer`, `StageRunnerCallback`.
- `de.sayayi.lib.stagerunner.exception` — `StageRunnerException`, `StageRunnerConfigurationException`.
- `de.sayayi.lib.stagerunner.spi` — reusable base implementations (`AbstractStageRunnerFactory`,
  `AbstractStageRunner`, `DefaultStageRunnerFactory`, `StageContextImpl`, ...).
- `de.sayayi.lib.stagerunner.spring` — Spring integration (`StageRunnerFactoryProcessor`,
  `StageFunctionBuilder`, `StageRunnerProxyBuilder`, `StageFunctionFilter`, `StageFunctionAnnotation`).
- `de.sayayi.lib.stagerunner.spring.annotation` — annotation building blocks (`StageDefinition`, `Data`).

The JPMS module name is `de.sayayi.lib.stagerunner`. Spring and Byte Buddy are declared as
`requires static`, so they are only needed on the module path when the Spring integration is used.

## Building from source

The project uses the Gradle wrapper and a Java 21 toolchain.

```bash
./gradlew build          # compile, test, and assemble
./gradlew test           # run unit tests
./gradlew javadoc        # generate API documentation
```

Build outputs are written to `.build/`.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text.

Copyright © 2024 Jeroen Gremmen.
