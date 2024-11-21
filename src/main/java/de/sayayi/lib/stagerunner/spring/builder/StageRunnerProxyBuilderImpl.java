package de.sayayi.lib.stagerunner.spring.builder;

import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import de.sayayi.lib.stagerunner.exception.StageRunnerConfigurationException;
import de.sayayi.lib.stagerunner.spring.StageRunnerProxyBuilder;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.*;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.assign.primitive.PrimitiveBoxingDelegate;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static de.sayayi.lib.stagerunner.spring.builder.ByteBuddyHelper.parameterizedType;
import static de.sayayi.lib.stagerunner.spring.builder.ByteBuddyHelper.typeDescription;
import static net.bytebuddy.description.modifier.Visibility.PRIVATE;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS;
import static net.bytebuddy.matcher.ElementMatchers.*;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class StageRunnerProxyBuilderImpl implements StageRunnerProxyBuilder
{
  private final boolean copyInterfaceMethodAnnotations;


  public StageRunnerProxyBuilderImpl(boolean copyInterfaceMethodAnnotations) {
    this.copyInterfaceMethodAnnotations = copyInterfaceMethodAnnotations;
  }


  @Override
  public @NotNull <R,S extends Enum<S>> R createProxy(@NotNull Class<S> stageType,
                                                      @NotNull Class<R> stageRunnerInterfaceType,
                                                      @NotNull Method stageRunnerInterfaceMethod,
                                                      @NotNull String[] dataNames,
                                                      @NotNull StageRunnerFactory<S> stageRunnerFactory)
  {
    var stageRunnerFactoryType = parameterizedType(StageRunnerFactory.class, stageType);
    var method = new MethodDescription.ForLoadedMethod(stageRunnerInterfaceMethod);

    try {
      //noinspection resource
      return new ByteBuddy()
          .with(new NamingStrategy.SuffixingRandom(stageType.getSimpleName()))
          .subclass(stageRunnerInterfaceType, NO_CONSTRUCTORS)
          .modifiers(PUBLIC)
          .defineField("factory", stageRunnerFactoryType, PRIVATE, FieldManifestation.FINAL)
          .defineConstructor(PUBLIC)
              .withParameters(stageRunnerFactoryType)
              .intercept(new ProxyConstructorImplementation())
          .define(stageRunnerInterfaceMethod)
              .intercept(new ProxyMethodImplementation(method, dataNames))
              .annotateMethod(copyInterfaceMethodAnnotations ? method.getDeclaredAnnotations() : List.of())
          .method(isToString())
              .intercept(FixedValue.value("Proxy implementation for interface " + stageRunnerInterfaceType.getName()))
          .make()
          .load(stageRunnerInterfaceType.getClassLoader())
          .getLoaded()
          .getDeclaredConstructor(StageRunnerFactory.class)
          .newInstance(stageRunnerFactory);
    } catch(Exception ex) {
      throw new StageRunnerConfigurationException("failed to create proxy for stage runner interface " +
          stageRunnerInterfaceType, ex);
    }
  }




  private static final class ProxyConstructorImplementation implements Implementation
  {
    @Override
    public @NotNull InstrumentedType prepare(@NotNull InstrumentedType instrumentedType) {
      return instrumentedType;
    }


    @Override
    public @NotNull ByteCodeAppender appender(@NotNull Target target)
    {
      return new ByteCodeAppender.Simple(
          // super();
          MethodVariableAccess.loadThis(),
          MethodInvocation.invoke(typeDescription(Object.class)
              .getDeclaredMethods()
              .filter(isConstructor())
              .getOnly()),
          // this.factory = factory(param 1)
          MethodVariableAccess.loadThis(),
          MethodVariableAccess.REFERENCE.loadFrom(1),
          FieldAccess
              .forField(target
                  .getInstrumentedType()
                  .getDeclaredFields()
                  .filter(named("factory"))
                  .getOnly())
              .write(),
          // return
          MethodReturn.VOID);
    }
  }




  private static final class ProxyMethodImplementation implements Implementation
  {
    private final MethodDescription method;
    private final String[] dataNames;


    private ProxyMethodImplementation(@NotNull MethodDescription method, @NotNull String[] dataNames)
    {
      this.method = method;
      this.dataNames = dataNames;
    }


    @Override
    public @NotNull InstrumentedType prepare(@NotNull InstrumentedType instrumentedType) {
      return instrumentedType;
    }


    @Override
    public @NotNull ByteCodeAppender appender(@NotNull Target target)
    {
      var stackManipulations = new ArrayList<StackManipulation>();

      // this.factory.createRunner() -> stack
      stackManipulations.add(MethodVariableAccess.loadThis());
      stackManipulations.add(FieldAccess
          .forField(target
              .getInstrumentedType()
              .getDeclaredFields()
              .filter(named("factory"))
              .getOnly())
          .read());
      stackManipulations.add(MethodInvocation.invoke(
          typeDescription(StageRunnerFactory.class)
              .getDeclaredMethods()
              .filter(named("createRunner"))
              .getOnly()));

      // param1 = data map
      stackManipulations.addAll(Stream.of(dataNames).anyMatch(Objects::nonNull)
          ? buildMapWithDataNames()
          : buildMapNoDataNames());

      // param2 = callback (optional)
      var stageRunnerCallbackParameter = findCallbackParameter();
      if (stageRunnerCallbackParameter != null)
        stackManipulations.add(MethodVariableAccess.load(stageRunnerCallbackParameter));

      stackManipulations.add(MethodInvocation.invoke(
          typeDescription(StageRunner.class)
              .getDeclaredMethods()
              .filter(named("run").and(takesArguments(stageRunnerCallbackParameter != null ? 2 : 1)))
              .getOnly()));

      if (method.getReturnType().represents(void.class))
      {
        stackManipulations.add(Removal.SINGLE);
        stackManipulations.add(MethodReturn.VOID);
      }
      else
        stackManipulations.add(MethodReturn.INTEGER);

      return new ByteCodeAppender.Simple(stackManipulations);
    }


    @Contract(pure = true)
    private @NotNull List<StackManipulation> buildMapWithDataNames()
    {
      var stackManipulations = new ArrayList<StackManipulation>();
      var hashMapType = typeDescription(HashMap.class);

      // new HashMap() -> stack
      stackManipulations.add(TypeCreation.of(hashMapType));
      stackManipulations.add(Duplication.SINGLE);
      stackManipulations.add(MethodInvocation.invoke(hashMapType
          .getDeclaredMethods()
          .filter(isDefaultConstructor())
          .getOnly()));

      var mapPutMethod = typeDescription(Map.class)
          .getDeclaredMethods()
          .filter(named("put").and(takesArguments(2)))
          .getOnly();
      String dataName;

      for(var parameter: method.getParameters())
        if ((dataName = dataNames[parameter.getIndex()]) != null)
        {
          stackManipulations.add(Duplication.SINGLE);
          stackManipulations.add(new TextConstant(dataName));
          stackManipulations.add(MethodVariableAccess.load(parameter));

          var parameterType = parameter.getType().asErasure();
          if (parameterType.isPrimitive())
          {
            stackManipulations.add(PrimitiveBoxingDelegate
                .forPrimitive(parameterType)
                .assignBoxedTo(
                    TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(Object.class),
                    Assigner.DEFAULT, Assigner.Typing.STATIC));
          }

          stackManipulations.add(MethodInvocation.invoke(mapPutMethod));
          stackManipulations.add(Removal.SINGLE);
        }

      stackManipulations.add(MethodInvocation.invoke(
          typeDescription(Map.class)
              .getDeclaredMethods()
              .filter(named("copyOf"))
              .getOnly()));

      return stackManipulations;
    }


    @Contract(pure = true)
    private @NotNull List<StackManipulation> buildMapNoDataNames()
    {
      return List.of(MethodInvocation.invoke(
          typeDescription(Map.class)
              .getDeclaredMethods()
              .filter(named("of").and(takesNoArguments()))
              .getOnly()));
    }


    @Contract(pure = true)
    private ParameterDescription findCallbackParameter()
    {
      var stageRunnerCallbackType = typeDescription(StageRunnerCallback.class);

      for(var parameter: method.getParameters())
        if (stageRunnerCallbackType.isAssignableFrom(parameter.getType().asErasure()))
          return parameter;

      return null;
    }
  }
}
