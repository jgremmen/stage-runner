package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageRunnerCallback;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.NamingStrategy.Suffixing.BaseNameResolver;
import net.bytebuddy.NamingStrategy.SuffixingRandom;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.Duplication;
import net.bytebuddy.implementation.bytecode.Removal;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.TypeCreation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveBoxingDelegate;
import net.bytebuddy.implementation.bytecode.constant.NullConstant;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.bytebuddy.description.modifier.TypeManifestation.FINAL;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.matcher.ElementMatchers.*;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class StageRunnerProxyBuilder<R,S extends Enum<S>>
{
  private static final MethodDescription METHOD_ABSTRACT_RUNNER_PROXY_RUN = TypeDescription.ForLoadedType
      .of(AbstractRunnerProxy.class)
      .getDeclaredMethods()
      .filter(named("run"))
      .getOnly();

  private static final MethodDescription CONSTRUCTOR_HASH_MAP = TypeDescription.ForLoadedType
      .of(HashMap.class)
      .getDeclaredMethods()
      .filter(isConstructor().and(takesNoArguments()))
      .getOnly();

  private static final MethodDescription METHOD_MAP_PUT = TypeDescription.ForLoadedType
      .of(Map.class)
      .getDeclaredMethods()
      .filter(named("put").and(takesArguments(2)))
      .getOnly();


  @SuppressWarnings("unchecked")
  public Class<? extends R> createFor(@NotNull Class<R> stageRunnerInterfaceType,
                                      @NotNull Method stageRunnerInterfaceMethod,
                                      @NotNull Class<S> stageType,
                                      @NotNull String[] dataNames,
                                      boolean copyAnnotations)
  {
    final TypeDescription.Generic abstractRunnerProxyType = TypeDescription.Generic.Builder
        .parameterizedType(AbstractRunnerProxy.class, stageType)
        .build();
    final MethodDescription method = new MethodDescription.ForLoadedMethod(stageRunnerInterfaceMethod);

    //noinspection resource
    return (Class<? extends R>)new ByteBuddy()
        .with(createNamingStrategy(stageRunnerInterfaceType))
        .subclass(abstractRunnerProxyType)
        .implement(stageRunnerInterfaceType)
        .modifiers(PUBLIC, FINAL)
        .define(stageRunnerInterfaceMethod)
            .intercept(implementFunctionalMethod(method, dataNames))
            .annotateMethod(copyAnnotations ? method.getDeclaredAnnotations() : List.of())
        .method(isToString())
            .intercept(FixedValue.value("Proxy for interface " + stageRunnerInterfaceType.getName()))
        .make()
        .load(stageRunnerInterfaceType.getClassLoader())
        .getLoaded();
  }


  @Contract(pure = true)
  protected @NotNull NamingStrategy createNamingStrategy(@NotNull Class<R> stageRunnerInterfaceType)
  {
    return new SuffixingRandom("",
        new BaseNameResolver.ForFixedValue(stageRunnerInterfaceType.getName() + "Impl"));
  }


  protected Implementation implementFunctionalMethod(@NotNull MethodDescription method,
                                                     @NotNull String[] dataNames)
  {
    final TypeDescription stageRunnerCallbackType = TypeDescription.ForLoadedType.of(StageRunnerCallback.class);
    final List<StackManipulation> stackManipulations = new ArrayList<>();
    ParameterDescription stageRunnerCallbackParameter = null;

    stackManipulations.add(MethodVariableAccess.loadThis());
    stackManipulations.add(TypeCreation.of(TypeDescription.ForLoadedType.of(HashMap.class)));
    stackManipulations.add(Duplication.SINGLE);
    stackManipulations.add(MethodInvocation.invoke(CONSTRUCTOR_HASH_MAP));

    for(final ParameterDescription parameter: method.getParameters())
    {
      final TypeDescription parameterType = parameter.getType().asErasure();

      if (stageRunnerCallbackType.isAssignableFrom(parameterType))
        stageRunnerCallbackParameter = parameter;
      else
      {
        stackManipulations.add(Duplication.SINGLE);
        stackManipulations.add(new TextConstant(dataNames[parameter.getIndex()]));
        stackManipulations.add(MethodVariableAccess.load(parameter));

        if (parameterType.isPrimitive())
        {
          stackManipulations.add(PrimitiveBoxingDelegate
              .forPrimitive(parameterType)
              .assignBoxedTo(
                  TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Object.class),
                  Assigner.DEFAULT, Assigner.Typing.STATIC));
        }

        stackManipulations.add(MethodInvocation.invoke(METHOD_MAP_PUT));
        stackManipulations.add(Removal.SINGLE);
      }
    }

    stackManipulations.add(stageRunnerCallbackParameter == null
        ? NullConstant.INSTANCE
        : MethodVariableAccess.load(stageRunnerCallbackParameter));
    stackManipulations.add(MethodInvocation.invoke(METHOD_ABSTRACT_RUNNER_PROXY_RUN));

    if (method.getReturnType().represents(void.class))
    {
      stackManipulations.add(Removal.SINGLE);
      stackManipulations.add(MethodReturn.VOID);
    }
    else
      stackManipulations.add(MethodReturn.INTEGER);

    return new Implementation.Simple(stackManipulations.toArray(new StackManipulation[0]));
  }
}
