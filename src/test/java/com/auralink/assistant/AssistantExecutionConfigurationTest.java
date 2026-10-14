package com.auralink.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class AssistantExecutionConfigurationTest {
 private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(AssistantExecutionConfiguration.class).withBean(ObjectMapper.class,ObjectMapper::new);
 @Test void disabledNeedsNoTablesDirectoriesTokensOrAi(){runner.run(c->{assertNull(c.getStartupFailure());assertEquals("ASSISTANT_EXECUTION_DISABLED",c.getBean(AssistantExecutionRuntime.class).state());});}
 @Test void malformedExecutionSettingsDoNotFailExistingApplication(){runner.withPropertyValues("auralink.assistant.enabled=true","auralink.assistant.execution.enabled=true","auralink.assistant.execution.concurrency=not-a-number").run(c->{assertNull(c.getStartupFailure());assertEquals("ASSISTANT_EXECUTION_MISCONFIGURED",c.getBean(AssistantExecutionRuntime.class).state());});}
 @Test void missingExecutionDependenciesDoNotEnableExecution(){runner.withPropertyValues("auralink.assistant.enabled=true","auralink.assistant.execution.enabled=true").run(c->{assertNull(c.getStartupFailure());assertFalse(c.getBean(AssistantExecutionRuntime.class).available());});}
 @Test void historyOnlyDoesNotInitializeDispatcherEvenWhenExecutionFlagWasLeftTrue(){runner.withPropertyValues("auralink.assistant.enabled=true","auralink.assistant.execution.enabled=true","auralink.assistant.history-only=true").run(c->{assertNull(c.getStartupFailure());assertEquals("ASSISTANT_HISTORY_ONLY",c.getBean(AssistantExecutionRuntime.class).state());assertFalse(c.getBean(AssistantExecutionRuntime.class).available());});}
 @Test void malformedHistoryModeFailsAssistantClosedWithoutBreakingApplication(){runner.withPropertyValues("auralink.assistant.enabled=true","auralink.assistant.execution.enabled=true","auralink.assistant.history-only=perhaps").run(c->{assertNull(c.getStartupFailure());assertEquals("ASSISTANT_EXECUTION_MISCONFIGURED",c.getBean(AssistantExecutionRuntime.class).state());});}
}
