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

    assertEquals(StageDef.class, sfa.getAnnotationType());
    assertEquals(TestStage.class, sfa.getStageType());
    assertEquals("scope", sfa.getNameProperty());
    assertEquals("stage", sfa.getStageProperty());
    assertEquals("priority", sfa.getOrderProperty());
    assertEquals("comment", sfa.getDescriptionProperty());
  }
}
