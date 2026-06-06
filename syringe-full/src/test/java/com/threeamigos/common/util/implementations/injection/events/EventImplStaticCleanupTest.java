package com.threeamigos.common.util.implementations.injection.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EventImpl static cleanup")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class EventImplStaticCleanupTest {

    @Test
    @DisplayName("clearStaticState shuts down default async executor and allows lazy recreation")
    void clearStaticStateShouldShutdownAndRecreateDefaultExecutor() throws Exception {
        Method accessor = EventImpl.class.getDeclaredMethod("getDefaultAsyncExecutor");
        accessor.setAccessible(true);
        ExecutorService ensuredExecutor = (ExecutorService) accessor.invoke(null);

        Field executorField = EventImpl.class.getDeclaredField("defaultAsyncExecutor");
        executorField.setAccessible(true);
        ExecutorService initialExecutor = (ExecutorService) executorField.get(null);

        assertNotNull(ensuredExecutor);
        assertNotNull(initialExecutor);
        assertFalse(initialExecutor.isShutdown());

        EventImpl.clearStaticState();
        assertTrue(initialExecutor.isShutdown());

        ExecutorService recreatedExecutor = (ExecutorService) accessor.invoke(null);

        assertNotNull(recreatedExecutor);
        assertFalse(recreatedExecutor.isShutdown());
    }
}
