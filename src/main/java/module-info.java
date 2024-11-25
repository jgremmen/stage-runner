module de.sayayi.lib.stagerunner {

  // optional requirement for Spring
  requires static net.bytebuddy;
  requires static spring.aop;
  requires static spring.core;
  requires static spring.beans;
  requires static spring.jcl;

  // compile time requirement
  requires static org.jetbrains.annotations;

  // exports
  exports de.sayayi.lib.stagerunner;
  exports de.sayayi.lib.stagerunner.exception;
  exports de.sayayi.lib.stagerunner.spi;
  exports de.sayayi.lib.stagerunner.spring;
  exports de.sayayi.lib.stagerunner.spring.annotation;

  // provide access to Spring
  opens de.sayayi.lib.stagerunner.spring to spring.core;
}