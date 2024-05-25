package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.StageRunner;
import de.sayayi.lib.stagerunner.StageRunnerCallback;
import de.sayayi.lib.stagerunner.StageRunnerFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.identityHashCode;
import static java.util.Collections.emptyMap;
import static org.springframework.util.ReflectionUtils.*;


final class StageRunnerInvocationHandler<S extends Enum<S>> implements InvocationHandler
{
  private final Class<?> stageRunnerInterfaceType;
  private final StageRunnerFactory<S> stageRunnerFactory;
  private final String[] dataNames;


  StageRunnerInvocationHandler(@NotNull Class<?> stageRunnerInterfaceType,
                               @NotNull StageRunnerFactory<S> stageRunnerFactory,
                               @NotNull String[] dataNames)
  {
    this.stageRunnerInterfaceType = stageRunnerInterfaceType;
    this.stageRunnerFactory = stageRunnerFactory;
    this.dataNames = dataNames;
  }


  @Override
  public Object invoke(Object proxy, Method method, Object[] args)
  {
    final Class<?> declaringClass = method.getDeclaringClass();

    if (declaringClass == Object.class)
    {
      if (isEqualsMethod(method))
        return proxy == args[0];

      if (isHashCodeMethod(method))
        return identityHashCode(proxy);

      if (isToStringMethod(method))
        return "Proxy for interface " + stageRunnerInterfaceType.getName();
    }

    if (declaringClass == stageRunnerInterfaceType)
      return invokeFunctionalMethod(args);

    throw new UnsupportedOperationException(method.getName());
  }


  @SuppressWarnings("unchecked")
  private boolean invokeFunctionalMethod(Object[] args)
  {
    final StageRunner<S> stageRunner = stageRunnerFactory.createRunner();
    final Map<String,Object> parameters;

    if (args != null)
    {
      StageRunnerCallback<S> callback = null;
      parameters = new HashMap<>();

      for(int n = 0; n < args.length; n++)
      {
        final Object argument = args[n];
        final String dataName = dataNames[n];

        if (argument instanceof StageRunnerCallback && (callback == null || dataName == null))
          callback = (StageRunnerCallback<S>)argument;

        if (dataName != null)
          parameters.put(dataName, argument);
      }

      if (callback != null)
        return stageRunner.run(parameters, callback);
    }
    else
      parameters = emptyMap();

    return stageRunner.run(parameters);
  }
}
