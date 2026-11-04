---
title: 0.3.4 -> 0.4.0
toc_depth: 2
---

# Version [0.4.0](https://github.com/jgremmen/stage-runner/tree/0.4.0) (2026-11-04)

## Breaking Changes

### Default callback is provided by a factory method

The constant `StageRunnerCallback.DEFAULT` has been removed. It was declared as `StageRunnerCallback<?>` and required
an unchecked cast whenever it was passed to `StageRunner.run(Map, StageRunnerCallback)`. Use the new static method
`StageRunnerCallback.defaultCallback()` instead. It returns the same stateless instance, typed for the requested stage
type:

```java
// 0.3.x
runner.run(data,
    (StageRunnerCallback<MyStage>)StageRunnerCallback.DEFAULT);

// 0.4.0
runner.run(data, StageRunnerCallback.defaultCallback());
```

### Typed stage function view

`StageContext.Function` is now a generic interface `StageContext.Function<S>`, and `getStage()` returns the stage type
`S` instead of `Enum<?>`. Accordingly, `StageContext.getFunctions()` returns `List<StageContext.Function<S>>`.
Variables declared as `List<StageContext.Function>` no longer compile and must be changed to the parameterized type;
custom `StageContext` implementations, such as test doubles, must adapt the return type of `getFunctions()`.
The returned list is now fully unmodifiable; previously, its elements could be replaced using `set`.

```java
List<StageContext.Function<MyStage>> functions =
    ctx.getFunctions();
MyStage stage = functions.get(0).getStage();  // no cast required
```

### Exception handling of stage functions

#### Errors are rethrown unchanged

An `Error` thrown by a stage function (for example an `AssertionError` or `OutOfMemoryError`) is no longer passed to
`StageRunnerCallback.stageExceptionHandler`. The function is marked as `FAILED`, `postStageFunctionCallback` is
invoked, and the error is rethrown unchanged by `StageRunner.run`. Previously, the default callback wrapped errors in a
`StageRunnerException`, and a custom exception handler could swallow them.

#### Configuration exceptions are passed to a separate handler

A `StageRunnerConfigurationException` thrown by a stage function is no longer passed to `stageExceptionHandler`, but to
the new handler `stageConfigurationExceptionHandler(StageContext, StageRunnerConfigurationException)`. Such exceptions
typically occur when a stage function adds another stage function at a position the run has already passed. The
default implementation rethrows the exception. A callback which overrides `stageExceptionHandler` in order to log
failures and continue does not receive these exceptions anymore; the run ends with the configuration exception unless
the new handler is overridden as well. If the exception has been caused by a rejected stage function, the run has
already been aborted, so `run` returns `false` after the handler returns normally.

```java
var callback = new StageRunnerCallback<MyStage>() {
  @Override
  public void stageExceptionHandler(
      StageContext<MyStage> ctx, Throwable ex) {
    log.warn("stage function failed", ex);
  }

  @Override
  public void stageConfigurationExceptionHandler(
      StageContext<MyStage> ctx,
      StageRunnerConfigurationException ex) {
    log.warn("invalid stage function", ex);
  }
};
```

#### Message of the wrapping exception

The default `stageExceptionHandler` still rethrows a `StageRunnerException` as is and wraps any other exception in a
`StageRunnerException`. The message of the wrapping exception has changed from the message of the cause (which could
be `null`) to a message naming the stage and the cause, for example
`stage function failed in stage PROCESS: java.lang.IllegalStateException: boom`. Code which evaluates the message
should use `getCause()` instead.

#### Missing primitive data in Spring stage functions

If a Spring stage function declares a primitive parameter and the corresponding data value is `null`, the generated
stage function now throws a `NullPointerException` with the message `Data value '<name>' must not be null`. Previously,
a `StageRunnerException` with the same message was thrown. With the default callback, the exception is wrapped in a
`StageRunnerException` as described above; custom exception handlers which check the exception type must be adjusted.

### Timing of `postStageCallback`

`StageRunnerCallback.postStageCallback` is now invoked directly after the last function of a stage has been executed.
While it is invoked, the stage context reports the completed stage as current stage, includes it in the processed
stages, and reports the stages which follow as remaining stages. Stage functions can be added to the following stages,
including from the `postStageCallback` of the last stage, in which case the run continues with these functions.

Previously, the callback was invoked when the first function of the next stage was about to start. At that time the
context already reported the next stage as current stage and omitted it from the remaining stages. For the last stage,
the callback was invoked after the run had terminated, so `getCurrentStage()` and `addStageFunction` failed with an
exception. In addition, the callback was invoked for the current stage even if the run had been
terminated by an exception, so a failed stage was reported as completed. It is now invoked neither after an abort nor
after an exception.

### Spring: stage context parameters

A stage function parameter now receives the stage context only if it is not annotated with `@Data` and its type is
`StageContext`. Previously, every parameter whose type could hold the stage context received it, including parameters
of type `StageFunctionConfigurer` or `Object`, and even parameters annotated with `@Data("name")`. Such parameters are
now bound to a data entry by name or type, and startup fails with a `StageRunnerConfigurationException` if no matching
entry exists. An `Object` parameter matches every data entry by type, so it may silently receive a data value instead
of the context; check such parameters explicitly. Declare these parameters as `StageContext` to receive the context:

```java
// 0.3.x
@StageDef(stage = MyStage.INIT)
public void init(StageFunctionConfigurer<MyStage> configurer) {
  configurer.addStageFunction(MyStage.END, ctx -> cleanUp());
}

// 0.4.0
@StageDef(stage = MyStage.INIT)
public void init(StageContext<MyStage> ctx) {
  ctx.addStageFunction(MyStage.END, c -> cleanUp());
}
```

### Spring: stage runner interface parameters

The parameters of the stage runner interface method are validated more strictly. Each violation is rejected with a
`StageRunnerConfigurationException` when the `StageRunnerFactoryProcessor` is created.

`@Data` without a name is no longer allowed on these parameters. Previously, the parameter name was used in that case;
remove the annotation or specify a name. `@Data` is not allowed on the `StageRunnerCallback` parameter either, and at
most one `StageRunnerCallback` parameter may be declared.

The callback parameter is now identified by its erased type, consistent with the generated proxy. Its stage type must
match the stage type of the stage function annotation, while a raw or wildcard typed callback is accepted. Previously,
the processor treated a callback for a different stage type as a data parameter, whereas the generated proxy passed it
as callback, so the value ended up in both places.

### Spring: stage function discovery

`StageRunnerFactoryProcessor` no longer implements `BeanPostProcessor`. Instead of registering the stage functions of
each bean as soon as the bean has been initialized, it implements `SmartInitializingSingleton` and discovers the stage
functions of all singleton beans in `afterSingletonsInstantiated`, once all non-lazy singletons have been instantiated.
The set of registered stage functions therefore no longer depends on the bean initialization order.

Running the stage runner before discovery has completed, for example from a `@PostConstruct` method, an
`InitializingBean` or a constructor, now fails with a `StageRunnerException`. Previously, the run silently executed the
stage functions registered up to that point. Start runs once the application context has been refreshed, for example
from an `ApplicationListener<ContextRefreshedEvent>` or, with Spring Boot, an `ApplicationRunner`.

Annotated methods are now detected using Spring's merged annotation search. Methods which were ignored before, such as
methods inheriting the annotation from an overridden or implemented method, or methods carrying a composed annotation,
are now registered as stage functions (see New Features). Use a `StageFunctionFilter` to exclude unwanted candidates.

Subclasses which override `postProcessAfterInitialization` must move their logic. The protected method
`analyseStageFunctions(Object)` is still invoked for every bean which is analyzed.

### Spring: `StageRunnerFactoryProcessor` fields and setters

All protected fields of `StageRunnerFactoryProcessor` have been made private. Subclasses use the new protected
accessors instead:

| Field in 0.3.x                   | Replacement in 0.4.0                                       |
|----------------------------------|------------------------------------------------------------|
| `logger`                         | none, declare a logger in the subclass                     |
| `stageRunnerInterfaceType`       | `getStageRunnerInterfaceType()`                            |
| `stageRunnerInterfaceMethod`     | `getStageRunnerInterfaceMethod()`                          |
| `stageFunctionAnnotation`        | `getStageFunctionAnnotation()`                             |
| `stageRunnerFactory`             | `getStageRunnerFactory(Class<S>)`                          |
| `dataNames`                      | `getStageRunnerParameters()`                               |
| `dataNameTypeMap`                | `getDataNameTypeMap()`                                     |
| `beanFactory`                    | `getBeanFactory()`                                         |
| `stageRunnerProxyBuilder`        | `getStageRunnerProxyBuilder()`                             |
| `stageFunctionBuilder`           | `getStageFunctionBuilder()`                                |
| `stageFunctionFilter`            | `getStageFunctionFilter()`                                 |
| `stageFunctionNameGenerator`     | `getStageFunctionNameGenerator()`                          |
| `conversionService`              | none, use `setConversionService`                           |
| `copyInterfaceMethodAnnotations` | none, use `setCopyInterfaceMethodAnnotations`              |

`getStageRunnerFactory(Class<S>)` returns the factory typed for the given stage type and throws an
`IllegalArgumentException` if the type does not match the stage type of the stage function annotation.
`getStageFunctionBuilder()` creates the default stage function builder on first access. The method
`getDataNameForParameter` is no longer accessible to subclasses.

`setConversionService` now throws an `IllegalStateException` once the stage function builder exists, and
`setCopyInterfaceMethodAnnotations` throws an `IllegalStateException` once the stage runner proxy builder exists.
Previously, both setters were silently ineffective in that case. As the default proxy builder is created in
`afterPropertiesSet`, invoke `setCopyInterfaceMethodAnnotations` in the bean method which creates the processor. The
setters now reject `null` arguments with a `NullPointerException` instead of an `IllegalArgumentException`.

### Spring: `StageFunctionFilter` signature

`StageFunctionFilter.filter` is no longer a generic method. The signature has changed from
`<B,S extends Enum<S>> boolean filter(B bean, S stage, int order, String name)` to
`boolean filter(Object bean, Enum<?> stage, int order, String name)`. Existing implementations must adapt the
signature. As the interface is now a regular functional interface, the filter can be written as a lambda:

```java
// 0.3.x
processor.setStageFunctionFilter(new StageFunctionFilter() {
  @Override
  public <B,S extends Enum<S>> boolean filter(
      B bean, S stage, int order, String name) {
    return stage != MyStage.DEBUG;
  }
});

// 0.4.0
processor.setStageFunctionFilter(
    (bean, stage, order, name) -> stage != MyStage.DEBUG);
```

### Spring: `StageRunnerProxyBuilder` signature

The parameter `String[] dataNames` of `StageRunnerProxyBuilder.createProxy` has been replaced by
`List<StageRunnerParameter> parameters`. The new sealed interface `StageRunnerParameter` describes each parameter of
the stage runner interface method in declaration order: a `StageRunnerParameter.DataParameter` carries the name of the
data entry, and a `StageRunnerParameter.CallbackParameter` marks the parameter used as callback. Previously, the
callback parameter was represented by a `null` entry in `dataNames` and had to be detected by its type. Custom proxy
builders must adapt the method signature:

```java
for(int p = 0; p < parameters.size(); p++)
{
  switch(parameters.get(p)) {
    case DataParameter(var name) -> data.put(name, args[p]);
    case CallbackParameter() ->
        callback = (StageRunnerCallback<S>)args[p];
  }
}
```

### `AbstractStageRunnerFactory.stageEnumType`

The protected field `AbstractStageRunnerFactory.stageEnumType` has been made private. Subclasses use the new protected
method `getStageEnumType()` instead.

## New Features

### Stage functions in front of the first function of a stage

`StageRunnerCallback.preStageCallback` may now add stage functions to the stage which is about to start, even if they
are positioned in front of the first stage function of that stage. Such a function becomes the first function of the
stage to be executed. Previously, these functions were rejected with a `StageRunnerConfigurationException` and the run
was aborted. `preStageCallback` is the latest point at which functions preceding the first function of a stage can be
added; from `preStageFunctionCallback`, functions positioned in front of the function about to be executed are still
rejected.

```java
var callback = new StageRunnerCallback<MyStage>() {
  @Override
  public void preStageCallback(StageContext<MyStage> ctx)
  {
    if (ctx.getCurrentStage() == MyStage.PROCESS)
      ctx.addStageFunction(MyStage.PROCESS, 0, "audit",
          c -> audit(c.getData("task")));
  }
};
```

### Spring: discovery of inherited and composed annotations

Stage function methods are now detected if the stage function annotation is declared on an overridden superclass
method or on an implemented interface method, or if the method carries a composed annotation which is meta-annotated
with the stage function annotation. To support composed annotations, the stage function annotation may declare
`ANNOTATION_TYPE` as target:

```java
@Target({ METHOD, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface StageDef
{
  @StageDefinition.Stage
  MyStage stage();
}

@Target(METHOD)
@Retention(RUNTIME)
@StageDef(stage = MyStage.CLEANUP)
public @interface Cleanup {
}

@Component
public class TempFiles
{
  @Cleanup
  public void deleteTempFiles() {
    // ...
  }
}
```

### Spring: discovery of lazy-init beans and factory bean products

Lazy-init singletons and objects created by factory beans are now instantiated during discovery if their type declares
stage functions, so their stage functions are always registered. Previously, the stage functions of a lazy-init bean
were only registered once the bean happened to be created, possibly after the stage runner had already been run. Beans
of other scopes are not considered.

### Spring: opting out of the stage context with `@Data`

`@Data` without a name may be placed on a stage function parameter which could hold the stage context, like a
parameter of type `StageContext`, `StageFunctionConfigurer` or `Object`. On a `StageContext` parameter, it prevents the
parameter from receiving the stage context; the parameter is then bound to the data entry named like the parameter or,
if there is none, to the entry matching its type. On any other parameter type, `@Data` without a name is rejected with
a `StageRunnerConfigurationException`.

```java
@StageDef(stage = MyStage.PROCESS)
public void process(@Data StageContext<?> forwardedContext) {
  // receives the data entry "forwardedContext"
}
```

### Spring: protected extension points of `StageRunnerFactoryProcessor`

Besides the accessors listed in the breaking changes, `StageRunnerFactoryProcessor` offers the new protected methods
`isStageFunction(Method)` and `hasStageFunctions(Class)`. Subclasses can override them to change which methods are
considered stage functions and which lazily created bean types are instantiated during discovery.

## Bug Fixes

### `null` arguments passed to a stage runner proxy

A generated stage runner proxy failed with a `NullPointerException` whenever one of the data arguments was `null`,
because the data map was built with `Map.copyOf`. `null` values are now stored in the data map. A `null` callback
argument also caused a `NullPointerException`; the default callback is now used instead.

### Rejected stage function corrupts the execution state

When a stage function was added through the stage context at a position the run had already passed, the function was
inserted before the exception was thrown. This shifted the index of the current function and corrupted the state
reported by the context. The position is now validated before the function is inserted. `enableNamedStageFunctions`
now validates all accepted functions first and enables none of them if one is rejected, instead of enabling some.

### `abort()` in a pre-callback does not prevent the next function from running

Invoking `StageContext.abort()` from `preStageCallback` or `preStageFunctionCallback` did not prevent the stage
function about to start from being executed. Now, no further stage function is executed. If the run is aborted from
`preStageFunctionCallback`, the function remains in state `WAITING` and `postStageFunctionCallback` is still invoked.

### Incorrect best match when resolving Spring stage function parameters by type

If a stage function parameter was resolved by type and several data entries matched, the first entry in iteration
order of the data map was selected instead of the entry with the strongest match. The strongest match is now chosen,
in the order identical type, assignable type, convertible type and finally `Object` parameter. Equally strong matches
of different entries are still rejected as ambiguous.

### Exception from `postStageFunctionCallback` masks the original failure

If a stage function failed and the exception handler threw an exception, an exception thrown by
`postStageFunctionCallback` replaced the original exception. The original exception is now propagated, and the
exception from `postStageFunctionCallback` is added to it as suppressed exception.

### Thread safety of the factory configuration

Registering stage functions with an `AbstractStageRunnerFactory` while runners were being executed on other threads
could lead to inconsistent copies of the function array or a `ConcurrentModificationException` while enabling named
stage functions. The configuration is now guarded by a read/write lock. Named stage functions enabled in a single call
are now added in registration order instead of hash order.

### Stage functions on JDK dynamic proxy beans fail at run time

If a bean with stage functions was wrapped in a JDK dynamic proxy, for example by Spring AOP, the generated stage
function cast the proxy to the target class and failed with a `ClassCastException` when the run started. The method
is now invoked through the corresponding interface method of the proxy. If the method is not exposed by any proxy
interface, startup fails with a `StageRunnerConfigurationException`.

### Package-private beans and parameter types

Stage functions declared in package-private bean classes, or using package-private parameter types, failed because the
generated class could not access them. Generated stage function classes are now defined in the package of the class
declaring the stage function method, and runner proxies in the package of the stage runner interface.

### Spring integration fails on the module path

The Spring integration did not work when the application was run as a named module. It now works, provided that the
packages containing the stage runner interface and the stage function beans are open to `de.sayayi.lib.stagerunner`,
and that the application module requires `net.bytebuddy` (or adds it with `--add-modules`). If a package is not open,
startup fails with a `StageRunnerConfigurationException` naming the package.

### Bridge methods are registered as additional stage functions

If a stage function overrode a generic superclass or interface method, the compiler-generated bridge method was
registered as a second stage function, so the method was executed twice per run. Bridge and synthetic methods are now
excluded.

### Static stage functions are registered once per bean

A static stage function was registered once for every singleton bean of its declaring class, so it was executed
multiple times per run. Static stage functions are now registered only once.

### `addStageFunctionCallback` not invoked for named stage functions

`StageRunnerCallback.addStageFunctionCallback` was only invoked for stage functions added through
`StageContext.addStageFunction`, but not for named stage functions enabled during a run. It is now invoked for each
enabled named stage function as well.

### `ConversionService` bean is created too early

`StageRunnerFactoryProcessor` looked up the `ConversionService` bean in `afterPropertiesSet`. As the processor is a
`BeanDefinitionRegistryPostProcessor`, this created the conversion service bean before the bean post processors had
been registered, so it was not processed by them. The conversion service is now resolved when the first stage function
is registered.

### Functional interface detection of the stage runner interface

The functional method of the stage runner interface was determined from all declared non-default methods. Static and
private methods, as well as redeclared `Object` methods such as `toString()`, were counted and caused the interface to
be rejected, while an abstract method inherited from a super-interface was not found. Only abstract methods, including
inherited ones, are now considered, ignoring `Object` methods.

### Failure in `preStageFunctionCallback` leaves the function in state `EXECUTING`

If `preStageFunctionCallback` threw an exception, the stage function remained in state `EXECUTING` although it had
never been executed. It is now reset to `WAITING`.

### Invalid stage function annotations are not detected

A stage function annotation without runtime retention was accepted by `StageFunctionAnnotation.buildFrom`, but no
stage function could ever be found. `buildFrom` now rejects annotations without runtime retention, as well as
annotations whose `@Target` includes neither `METHOD` nor `ANNOTATION_TYPE`.

### Error reporting for incompatible data types and class generation failures

If a stage function parameter annotated with `@Data("name")` had a type incompatible with that data entry, an
`IllegalStateException` without context was thrown. This is now a `StageRunnerConfigurationException` naming the data
entry, its type, the parameter and the method. Linkage errors during class generation, which occur when the class
loader of the bean or interface cannot load the library or Spring, are now reported as
`StageRunnerConfigurationException` naming the class loader.

### Empty name from a custom stage function name generator

If a custom stage function name generator returned an empty string, the stage function was registered as named stage
function and rejected because of the empty name. An empty name now registers a regular, unnamed stage function.
