# Stage Runner

A lightweight Java library for executing an ordered sequence of *stage functions*: small units of work organized
around the constants of a user-defined stage `enum`.

Stage Runner lets you model a workflow as a series of well-defined stages (for example `INIT`, `START`, `PROCESS`,
`CLEANUP`, `END`), register one or more functions per stage, and run them in a predictable order. While the runner is
executing, functions can share data, inspect progress, add new work dynamically, or abort the run altogether.

An optional Spring integration wires stage functions directly from annotated methods on Spring managed beans.

## Features

- Type-safe stages driven by any user-defined `enum`.
- Per-stage function order, plus optional descriptions surfaced through callbacks.
- Shared data map available to every stage function through the `StageContext`.
- Named stage functions that are only executed when enabled for a run.
- Dynamic registration of additional stage functions while the runner is executing.
- Callback hooks for stage and stage function boundaries, function additions and exception handling.
- Cooperative abort through `StageContext.abort()`.
- Thread-safe factory configuration; runners can be executed concurrently.
- Ready-to-use `DefaultStageRunnerFactory`, or subclass `AbstractStageRunnerFactory` for full control.
- Optional Spring integration that binds an application-defined runner interface to annotated beans.
- Java Platform Module System (JPMS) ready: the library is the named module `de.sayayi.lib.stagerunner`.

## Requirements

- Java 21 or newer.
- Optional runtime dependencies, only required for the Spring integration:
  - Spring Framework 5.3 up to 6.x (`spring-context`).
  - Byte Buddy 1.15 up to 1.18 (used to generate the runner proxy and stage function classes).

## Installation

The library is published to Maven Central under the coordinates:

- **Group id:** `de.sayayi.lib`
- **Artifact id:** `stage-runner`

Spring and Byte Buddy are declared as *optional* dependencies, so they are not added to your project transitively.
Add them yourself if you use the Spring integration.

### Gradle

```groovy
dependencies {
  implementation 'de.sayayi.lib:stage-runner:<version>'

  // only needed for the Spring integration
  implementation 'org.springframework:spring-context:<spring-version>'
  implementation 'net.bytebuddy:byte-buddy:<byte-buddy-version>'
}
```

When your build manages Spring versions through a BOM (for example with Spring Boot), the Spring and Byte Buddy
versions can be omitted.

### Maven

```xml
<dependency>
  <groupId>de.sayayi.lib</groupId>
  <artifactId>stage-runner</artifactId>
  <version>${stage-runner.version}</version>
</dependency>

<!-- only needed for the Spring integration -->
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-context</artifactId>
  <version>${spring.version}</version>
</dependency>
<dependency>
  <groupId>net.bytebuddy</groupId>
  <artifactId>byte-buddy</artifactId>
  <version>${byte-buddy.version}</version>
</dependency>
```

## Core concepts

| Type                                                       | Purpose                                                                                            |
|------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `StageFunction<S>`                                         | Functional interface implemented by each unit of work. Receives a `StageContext`.                  |
| `StageFunctionConfigurer<S>`                               | API for registering stage functions with a stage, order, and optional description.                 |
| `StageFunctionConfigurer.Named<S>`                         | Extension for registering *named* stage functions that can be enabled at runtime.                  |
| `StageRunnerFactory<S>`                                    | Creates `StageRunner` instances sharing the same stage function configuration.                     |
| `StageRunner<S>`                                           | Executes the configured stage functions, using an input data map.                                  |
| `StageContext<S>`                                          | Runtime view: current stage, processed/remaining stages, shared data, abort, dynamic registration. |
| `StageRunnerCallback<S>`                                   | Optional hook interface with notification callbacks and exception handlers.                        |
| `DefaultStageRunnerFactory<S>`                             | Ready-to-use factory backed by `AbstractStageRunner`.                                              |
| `AbstractStageRunnerFactory<S>` / `AbstractStageRunner<S>` | Base classes for custom factories/runners.                                                         |
| `StageRunnerException`                                     | Unchecked base exception of the library.                                                           |
| `StageRunnerConfigurationException`                        | Unchecked exception signalling an invalid configuration.                                           |

Runner instances can be run any number of times. Each run uses its own `StageContext`, so runs share nothing but the
stage functions configured on the factory.

## Usage

### Quick start

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

import java.util.Map;

var factory = new DefaultStageRunnerFactory<>(MyStage.class);

factory.addStageFunction(MyStage.START, ctx -> System.out.println("starting"));
factory.addStageFunction(MyStage.PROCESS, "process task", ctx -> {
  String task = ctx.getData("task");
  System.out.println("processing " + task);
});
factory.addStageFunction(MyStage.END, ctx -> System.out.println("done"));

StageRunner<MyStage> runner = factory.createRunner();
boolean completed = runner.run(Map.of("task", "import-users"));
```

Stage functions are executed in stage order. Within a stage, functions with a lower `order` are executed first
(defaulting to `StageFunctionConfigurer.DEFAULT_ORDER = 1000`); functions with the same stage and order are executed
in the order they have been added.

`run` returns `true` if all stage functions have been executed and `false` if the run has been aborted.

### Using the stage context

Inside a stage function you have full access to the ongoing run:

```java
factory.addStageFunction(MyStage.PROCESS, ctx -> {
  MyStage current   = ctx.getCurrentStage();
  Set<MyStage> done = ctx.getProcessedStages();
  Set<MyStage> todo = ctx.getRemainingStages();

  Integer count = ctx.getData("count");
  if (count == null || count <= 0)
  {
    ctx.abort();  // no further stage functions are executed; run() returns false
    return;
  }

  // dynamically add another stage function for a later stage
  ctx.addStageFunction(MyStage.CLEANUP, "close resources", c -> closeResources(c));
});
```

`getData` casts the value to the expected type without conversion. `getFunctions()` returns a snapshot of all stage
functions of the run, each with its stage, order, description and `FunctionState` (`WAITING`, `EXECUTING`,
`PROCESSED` or `FAILED`).

A stage context belongs to the thread executing the run and is not thread safe.

### Adding stage functions during a run

Functions added through the `StageContext` only apply to the current run. Their position, given by stage and order,
must not lie before the stage function currently being executed:

- A function positioned after the current stage function can always be added, unless its stage has already been
  processed.
- A function positioned in front of the first function of a stage can only be added from
  `StageRunnerCallback.preStageCallback`, and only to the stage which is about to start.
- From `preStageFunctionCallback`, no function can be added in front of the function about to be executed.

A function which cannot be added is rejected: the run is aborted and a `StageRunnerConfigurationException` is thrown.
The same rules apply to enabling named stage functions.

### Named stage functions

Named functions are registered with the factory but only executed when explicitly enabled during a run, which is
useful for optional or feature-flagged behavior. Names must be non-empty and unique per factory.

```java
factory.namedStageFunction("audit", MyStage.END, ctx -> writeAuditLog(ctx));

factory.addStageFunction(MyStage.START, ctx -> {
  if (Boolean.TRUE.equals(ctx.getData("auditing")))
    ctx.enableNamedStageFunction("audit");
});
```

You can also enable a group of functions by name predicate. The names of the newly enabled functions are returned:

```java
Set<String> enabled = ctx.enableNamedStageFunctions(name -> name.startsWith("metrics."));
```

An enabled function only applies to the current run.

### Callbacks

Supply a `StageRunnerCallback` to observe or influence execution. All methods have a default implementation, so you
only override what you need. `run(data)` uses `StageRunnerCallback.defaultCallback()`.

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
    // throw to end the run, or return normally to continue with the remaining functions
    StageRunnerCallback.super.stageExceptionHandler(ctx, exception);
  }
});
```

For each stage function, the callback methods are invoked in this order:

1. `preStageCallback`, if the function is the first one of its stage.
2. `preStageFunctionCallback`.
3. `stageExceptionHandler` or `stageConfigurationExceptionHandler`, if the function has failed.
4. `postStageFunctionCallback`, regardless of whether the function has succeeded. It is not invoked if
   `preStageFunctionCallback` has thrown an exception; the function is then not executed and the exception is
   propagated to the caller of `run`.
5. `postStageCallback`, if the function is the last one of its stage and the run has neither been aborted nor
   terminated by an exception.

`addStageFunctionCallback` is invoked whenever a stage function is added or a named stage function is enabled during
the run.

### Exception handling

- An exception thrown by a stage function is passed to `stageExceptionHandler`. The default implementation rethrows a
  `StageRunnerException` as is and wraps any other exception in a `StageRunnerException`.
- A `StageRunnerConfigurationException`, for example from adding a stage function at an invalid position, is passed
  to `stageConfigurationExceptionHandler` instead. The default implementation rethrows it.
- If a handler returns normally, the function is marked as failed and the run continues with the next function,
  unless the run has been aborted (a rejected stage function always aborts the run). If a handler throws, the run
  ends and `run` throws instead of returning `false`.
- An `Error` thrown by a stage function is never passed to a handler; it terminates the run and is rethrown unchanged.
- An exception thrown by `postStageFunctionCallback` while the run is already terminating does not replace the
  original exception; it is added to it as a suppressed exception.

### Thread safety

The configuration methods of `AbstractStageRunnerFactory` are thread safe, and runners can be executed concurrently.
A run uses a snapshot of the regular stage functions taken when it starts; functions added to the factory later only
apply to subsequent runs. Named stage functions become available for enabling immediately.

### Custom factories

For advanced use cases, subclass `AbstractStageRunnerFactory` and return a custom `AbstractStageRunner` subclass
from `createRunner()`, for example to expose an application-specific runner API. See `DefaultStageRunnerFactory`
for the minimal template.

## Spring integration

The `de.sayayi.lib.stagerunner.spring` package binds a user-defined runner interface to Spring managed beans. Stage
functions are declared as methods carrying a stage function annotation you define using the building blocks in
`de.sayayi.lib.stagerunner.spring.annotation.StageDefinition`.

### 1. Define your stage function annotation

```java
import de.sayayi.lib.stagerunner.StageFunctionConfigurer;
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
  int priority() default StageFunctionConfigurer.DEFAULT_ORDER;

  @StageDefinition.Description
  String comment() default "";
}
```

The annotation must have runtime retention and be applicable to methods (or to annotation types, for use as a
meta-annotation). A `@Stage` property returning the stage enum is mandatory. `@Order` (`int`), `@Description` and
`@Name` (both `String`) are optional, and each may be used at most once. A method with a non-empty name is registered
as a named stage function. An empty description is treated as no description, so callbacks receive `null`.

### 2. Define a runner interface

The interface's single abstract method describes the input parameters that are passed to the stage runner as data
map. Abstract methods inherited from super-interfaces count; default, static and private methods, as well as
redeclared `Object` methods, are ignored. The method must return `boolean` or `void`.

```java
import de.sayayi.lib.stagerunner.spring.annotation.Data;

import java.util.List;

public interface MyRunner
{
  boolean run(String task, @Data("map") List<Integer> params, int count);
}
```

Each parameter is stored in the data map under its parameter name, or under the name given by `@Data`. Using
parameter names requires the interface to be compiled with `-parameters`. A parameter of type `StageRunnerCallback`
is not passed as data but used as callback for the run; if its value is `null`, the default callback is used. At most
one callback parameter is allowed and its stage type, if specified, must match the stage type of the annotation.

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

Stage function method parameters may include a `StageContext` and any values from the data map:

- a parameter annotated with `@Data("name")` receives the data entry with that name, which must exist;
- an unannotated `StageContext` parameter receives the stage context;
- any other parameter receives the data entry named like the parameter or, if there is none, the entry matching its
  type.

Matching by parameter name requires the classes to be compiled with `-parameters`. Without it, parameters which are
not annotated with `@Data("name")` are matched by type only.

Like the stage function annotation, `@Data` is inherited: if the stage function is declared on an interface or
superclass method, the `@Data` annotations of that method's parameters apply to the implementation as well. An
annotation on the implementing method's parameter takes precedence.

Values are converted to the parameter type with Spring's `ConversionService` where necessary (in the example above,
`List<Integer>` is converted to `List<Long>`). `@Data` without a name is only allowed on a parameter which could
receive the stage context; it keeps the stage context away from that parameter. Invalid bindings are rejected at
startup. See the `@Data` Javadoc for the complete rules.

### 4. Register the factory processor

```java
@Configuration
public class StageConfiguration
{
  @Bean
  static StageRunnerFactoryProcessor<MyRunner> stageRunnerProcessor() {
    return new StageRunnerFactoryProcessor<>(MyRunner.class, StageDef.class);
  }
}
```

The processor is a `BeanDefinitionRegistryPostProcessor`, so declare its bean method `static`. It registers a lazily
created, primary singleton bean implementing `MyRunner`, named after the fully qualified interface name, which you can
inject anywhere:

```java
@Autowired MyRunner myRunner;

myRunner.run("import-users", List.of(1, 67, -4), 56);
```

### How stage functions are discovered

Once all non-lazy singletons have been instantiated, the processor:

- scans the public methods of all singleton beans for methods annotated with `@StageDef` (also when the annotation
  is inherited from an overridden or implemented method, or present on a composed annotation),
- passes each candidate to the `StageFunctionFilter`, skipping rejected candidates,
- turns each accepted method into a `StageFunction` (using the `StageFunctionBuilder`), and
- registers it with the stage runner factory behind the proxy, as named stage function if it has a name.

Notes:

- Lazy-init singletons and objects created by factory beans are only instantiated if their *declared* type (the
  return type of the `@Bean` method, the bean class or the factory bean's object type) declares stage functions. A
  lazy `@Bean` method returning an interface whose implementation declares the stage functions is therefore skipped.
  Declare the implementation class as return type, or enable `setCreateLazySingletons` to instantiate all of them.
  Beans of other scopes are not considered.
- Static methods are supported and registered once, even if several beans expose them.
- Beans proxied by Spring AOP are supported. For JDK dynamic proxies, the stage function method must be exposed by
  one of the proxy interfaces.
- Package-private bean classes and parameter types are supported.
- Running the stage runner before discovery has completed, for example from a `@PostConstruct` method, fails with a
  `StageRunnerException`.

### Customization

`StageRunnerFactoryProcessor` offers setters for adjusting the registration:

| Setter                              | Purpose                                                                                       |
|-------------------------------------|-----------------------------------------------------------------------------------------------|
| `setStageFunctionFilter`            | Decides which discovered stage functions are registered (all by default).                     |
| `setStageFunctionNameGenerator`     | Derives the stage function name from the annotation attributes and the method.                |
| `setConversionService`              | Conversion service for parameter values (default: the unique `ConversionService` bean, or the shared default conversion service). |
| `setCopyInterfaceMethodAnnotations` | Copies the annotations of the runner interface method to the generated proxy method.          |
| `setCreateLazySingletons`           | Instantiates all lazy-init singletons and factory bean products during discovery, regardless of their declared type. |
| `setStageFunctionBuilder`           | Replaces the default stage function builder.                                                  |
| `setStageRunnerProxyBuilder`        | Replaces the default runner proxy builder.                                                    |

`setConversionService` and `setCopyInterfaceMethodAnnotations` only affect the default builders and must be invoked
before the corresponding builder exists. `setCreateLazySingletons` must be invoked before the stage functions are
discovered. For further customization, subclass the processor and use its protected
methods.

The generated proxy implements `FactoryAccessor`, which gives access to the underlying factory for registering
additional stage functions programmatically:

```java
@SuppressWarnings("unchecked")
var accessor = (FactoryAccessor<MyStage>)myRunner;

accessor.getStageFunctionConfigurer().addStageFunction(MyStage.END, ctx -> notifyListeners());
```

## Modules and packages

| Package                                       | Contents                                                                                                 |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `de.sayayi.lib.stagerunner`                   | Public API: `StageRunner`, `StageRunnerFactory`, `StageContext`, `StageFunction`, `StageFunctionConfigurer`, `StageRunnerCallback`. |
| `de.sayayi.lib.stagerunner.exception`         | `StageRunnerException`, `StageRunnerConfigurationException`.                                             |
| `de.sayayi.lib.stagerunner.spi`               | Base implementations: `AbstractStageRunnerFactory`, `AbstractStageRunner`, `DefaultStageRunnerFactory`.  |
| `de.sayayi.lib.stagerunner.spring`            | Spring integration: `StageRunnerFactoryProcessor`, `StageFunctionBuilder`, `StageRunnerProxyBuilder`, `StageFunctionFilter`, `StageFunctionAnnotation`, `StageRunnerParameter`, `FactoryAccessor`. |
| `de.sayayi.lib.stagerunner.spring.annotation` | Annotation building blocks: `StageDefinition`, `Data`.                                                   |

The default builder implementations in `de.sayayi.lib.stagerunner.spring.builder` are internal and not exported.

The JPMS module name is `de.sayayi.lib.stagerunner`. Spring and Byte Buddy are declared as `requires static`, so they
are only needed on the module path when the Spring integration is used.

When using the Spring integration from a named application module:

- Byte Buddy must be resolved at runtime. As it is an optional dependency of the library, require it in your module
  (or add it with `--add-modules net.bytebuddy`).
- The packages containing the stage runner interface and the beans with stage functions must be open to the library,
  because the generated stage functions and runner proxies are defined in these packages.

```java
module my.app {
  requires de.sayayi.lib.stagerunner;
  requires net.bytebuddy;
  requires spring.context;

  opens my.app.stages to de.sayayi.lib.stagerunner, spring.core, spring.beans, spring.context;
}
```

The generated classes are also defined in the class loader of the stage runner interface and of the classes declaring
the stage functions. In hierarchical class loader setups, such as application servers or plugin systems, these class
loaders must be able to load the stage runner library and Spring. Otherwise, the application fails at startup with a
`StageRunnerConfigurationException` naming the class loader.

## Building from source

The project uses the Gradle wrapper and a Java 21 toolchain.

```bash
./gradlew build          # compile, test, and assemble
./gradlew test           # run unit tests (includes the module path tests)
./gradlew moduleTest     # run the module path integration tests only
./gradlew javadoc        # generate API documentation
```

Build outputs are written to `.build/`.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text.

Copyright © 2024 Jeroen Gremmen.
