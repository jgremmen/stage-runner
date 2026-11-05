---
title: 0.4.0 -> 0.4.1
toc_depth: 2
---

# Version [0.4.1](https://github.com/jgremmen/stage-runner/tree/0.4.1) (2026-11-05)

## Breaking Changes

### `null` names are rejected by the stage context

`StageContext.getData(String)` now throws a `NullPointerException` if the name is `null`. Previously, the result
depended on the data map passed to `StageRunner.run`: a `HashMap` returned the value stored for the `null` key (usually
`null`), while an immutable map threw a `NullPointerException`. The behavior is now independent of the map
implementation.

`enableNamedStageFunction(String)` and `enableNamedStageFunctions(Predicate)` now check their argument first and throw
a `NullPointerException` for `null`, even if the run has already terminated or no named stage functions exist.
Previously, a `StageRunnerConfigurationException` was thrown in the first case, and the default implementation of
`enableNamedStageFunction` in `StageContext` did not reject a `null` name at all. Custom `StageContext`
implementations which rely on the default method now receive a `NullPointerException` before their
`enableNamedStageFunctions` implementation is invoked.

### Spring: discovery settings are fixed after discovery

The setters `setStageFunctionBuilder`, `setStageFunctionFilter` and `setStageFunctionNameGenerator` of
`StageRunnerFactoryProcessor` now throw an `IllegalStateException` once the stage functions have been discovered in
`afterSingletonsInstantiated`. Previously, they accepted a new value at any time, although it no longer had an effect
on the registered stage functions. Configure the processor in the bean method which creates it, before the application
context is refreshed.

## New Features

### Spring: creating all lazy-init singletons during discovery

Lazy-init singletons and objects created by factory beans are only instantiated during discovery if their *declared*
type has stage functions. The declared type is the return type of the `@Bean` method, the bean class or the object type
of the factory bean. If the stage functions are declared only by the actual class of the bean, for example the
implementation returned by a `@Bean` method with an interface as return type, the bean is not created and its stage
functions are not discovered. Such beans are now logged at debug level.

The new setter `StageRunnerFactoryProcessor.setCreateLazySingletons(boolean)` instantiates all lazy-init singletons and
factory bean products during discovery, regardless of their declared type, so the actual class of each bean is
analyzed. The setting is disabled by default, as it defeats the purpose of lazy initialization. Alternatively, declare
the implementation class as return type of the `@Bean` method.

```java
@Bean
public static StageRunnerFactoryProcessor<OrderRunner>
    orderRunnerProcessor()
{
  var processor = new StageRunnerFactoryProcessor<>(
      OrderRunner.class, StageDef.class);
  processor.setCreateLazySingletons(true);
  return processor;
}

@Bean
@Lazy
public OrderValidator orderValidator() // interface type
{
  // stage functions declared by the implementation
  return new DefaultOrderValidator();
}
```

### Spring: inherited `@Data` annotations

Like the stage function annotation, `@Data` is now inherited from overridden or implemented methods. A stage function
parameter without `@Data` uses the annotation of the corresponding parameter of the closest method it overrides or
implements, including generic interface methods. The interfaces of a class are searched before its superclass. An
annotation on the parameter of the implementing method takes precedence.

```java
public interface OrderStep
{
  @StageDef(stage = MyStage.PROCESS)
  void process(@Data("order") Order order,
      @Data("customer") Customer customer);
}

@Component
public class ShippingStep implements OrderStep
{
  @Override
  public void process(Order order, Customer customer) {
    // receives the data entries "order" and "customer"
  }
}
```

## Bug Fixes

### Stage functions of scoped proxy beans are executed twice

A bean with a scoped proxy is registered both as the scoped proxy and as its hidden target bean
`scopedTarget.<name>`. Both were analyzed during discovery, so each stage function of a singleton target was
registered and executed twice per run. The scoped target is now skipped, and its stage functions are discovered and
invoked through the scoped proxy. The class scanned for stage functions is determined from the target bean definition,
so the result no longer depends on whether the proxy or its target has been created first, or on whether the target is
proxied itself, for example by `@Async` or `@Cacheable`. For an interface based scoped proxy, the stage functions were
previously invoked on the target bean directly, bypassing the proxy; they are now invoked through the proxy. A bean
instance which is exposed by more than one bean name was registered once per name and is now analyzed only once.

### `@Data` on inherited method parameters is ignored

A stage function declared on an interface or superclass method was detected, but the `@Data` annotations on the
parameters of that method were ignored. The parameters of the implementing method were then bound by parameter name or
type instead, which could select a different data entry or fail at startup with a `StageRunnerConfigurationException`.
The annotations are now inherited as described in New Features.

### Same-named bean classes from different class loaders share one generated class

The cache of generated stage function classes identified a method by its name and signature only. If two bean classes
with the same fully qualified name were loaded by different class loaders, the stage function of the second class
reused the class generated for the first one, which targets the wrong class. The cache now distinguishes methods by
their declaring class, so each bean class receives its own generated stage function class.

### Empty stage function description is passed on as empty string

If the description property of a stage function annotation was empty, for example because of a `default ""` declaration,
the empty string was passed to `StageRunnerCallback.preStageFunctionCallback` as description. An empty description is
now treated as no description, so `StageFunctionAnnotation.getDescription` returns `null` and the stage function is
registered without description.
