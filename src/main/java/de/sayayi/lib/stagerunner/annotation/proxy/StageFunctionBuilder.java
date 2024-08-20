package de.sayayi.lib.stagerunner.annotation.proxy;

import de.sayayi.lib.stagerunner.StageContext;
import de.sayayi.lib.stagerunner.StageFunction;
import de.sayayi.lib.stagerunner.annotation.AbstractStageFunctionBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import static de.sayayi.lib.stagerunner.annotation.AbstractStageFunctionBuilder.TypeQualifier.CONVERTABLE;


public class StageFunctionBuilder extends AbstractStageFunctionBuilder
{
  public StageFunctionBuilder(@NotNull Class<? extends Annotation> stageFunctionAnnotationType,
                              @NotNull ConversionService conversionService,
                              @NotNull Map<String,ResolvableType> dataTypeMap) {
    super(stageFunctionAnnotationType, conversionService, dataTypeMap);
  }


  @Override
  protected @NotNull <S extends Enum<S>> StageFunction<S> buildFor(
      @NotNull Object bean, @NotNull Method method, @NotNull NameWithQualifierAndType[] parameters)
  {
    final int parameterCount = method.getParameterCount();

    //noinspection unchecked
    final Function<StageContext<S>,Object>[] parameterFunctions = new Function[parameterCount];
    final StageFunctionAdapter<S> stageFunctionAdapter = new StageFunctionAdapter<>(bean, method);

    for(int p = 0; p < parameterCount; p++)
    {
      final NameWithQualifierAndType nqt = parameters[p];
      final String dataName = nqt.getName();

      if ("$context".equals(dataName))
        parameterFunctions[p] = ctx -> ctx;
      else
      {
        final boolean doConversion = nqt.getQualifier() == CONVERTABLE;

        parameterFunctions[p] = ctx -> {
          final Object data = ctx.getData(dataName);
          return doConversion
              ? conversionService.convert(data, TypeDescriptor.forObject(data), nqt.getType())
              : data;
        };
      }
    }

    stageFunctionAdapter.setParameterFunctions(parameterFunctions);

    return stageFunctionAdapter;
  }
}
