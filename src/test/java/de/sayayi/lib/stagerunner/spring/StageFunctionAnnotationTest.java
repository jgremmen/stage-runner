package de.sayayi.lib.stagerunner.spring;

import de.sayayi.lib.stagerunner.TestStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


@DisplayName("Stage function annotation")
class StageFunctionAnnotationTest
{
  @Test
  @DisplayName("Fully qualified function annotation")
  void fqAnnotation()
  {
    final StageFunctionAnnotation sfa = StageFunctionAnnotation.buildFrom(StageDef.class);

    assertEquals(StageDef.class, sfa.annotationType);
    assertEquals(TestStage.class, sfa.stageType);
    assertEquals("stage", sfa.stageProperty);
    assertEquals("priority", sfa.orderProperty);
    assertEquals("comment", sfa.descriptionProperty);
  }
}
