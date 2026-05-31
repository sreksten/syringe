package com.threeamigos.common.util.implementations.injection.stressloop;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.events.EventImpl;
import com.threeamigos.common.util.implementations.injection.events.propagation.ConversationPropagationRegistry;
import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationImpl;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stress loop memory and cleanup coverage")
@Execution(ExecutionMode.SAME_THREAD)
class SyringeStressLoopMemoryTest {

    private static final int WARMUP_CYCLES = 4;
    private static final int MEASURED_CYCLES = 24;
    private static final int CHECKPOINT_EVERY = 4;

    private static final long MAX_HEAP_GROWTH_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_LOADED_CLASS_GROWTH = 1800;
    private static final int MAX_RETAINED_DEPLOYMENT_CLASSLOADERS = 1;

    private static final AtomicInteger INTERCEPTOR_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger DECORATOR_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger SYNC_OBSERVER_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger ASYNC_OBSERVER_INVOCATIONS = new AtomicInteger();

    @Test
    @DisplayName("Repeated setup/shutdown should keep heap and classloader growth bounded while using interceptors, decorators, observers and scopes")
    void shouldKeepMemoryAndClassloadersStableAcrossRepeatedContainerLifecycles() throws Exception {
        EventImpl.clearStaticState();
        ConversationImpl.clearAllGlobalState();
        ConversationPropagationRegistry.clear();
        DestroyedInstanceTracker.clear();

        List<WeakReference<ClassLoader>> deploymentClassLoaders = new ArrayList<WeakReference<ClassLoader>>();
        List<Snapshot> checkpoints = new ArrayList<Snapshot>();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();

        for (int i = 0; i < WARMUP_CYCLES; i++) {
            executeSingleLifecycle(i, deploymentClassLoaders, false);
        }

        resetCounters();
        forceGc();
        Snapshot baseline = Snapshot.capture(memoryMXBean, classLoadingMXBean, deploymentClassLoaders, 0);

        for (int i = 0; i < MEASURED_CYCLES; i++) {
            executeSingleLifecycle(i + 1, deploymentClassLoaders, true);
            if ((i + 1) % CHECKPOINT_EVERY == 0) {
                forceGc();
                checkpoints.add(Snapshot.capture(memoryMXBean, classLoadingMXBean, deploymentClassLoaders, i + 1));
            }
        }

        forceGcRepeatedly(8);
        Snapshot end = Snapshot.capture(memoryMXBean, classLoadingMXBean, deploymentClassLoaders, MEASURED_CYCLES);

        assertEquals(MEASURED_CYCLES, INTERCEPTOR_INVOCATIONS.get(), "Unexpected interceptor invocation count");
        assertEquals(MEASURED_CYCLES, DECORATOR_INVOCATIONS.get(), "Unexpected decorator invocation count");
        assertEquals(MEASURED_CYCLES * 2, SYNC_OBSERVER_INVOCATIONS.get(), "Unexpected sync observer count");
        assertEquals(MEASURED_CYCLES, ASYNC_OBSERVER_INVOCATIONS.get(), "Unexpected async observer count");

        long heapGrowth = end.heapUsedBytes - baseline.heapUsedBytes;
        int loadedClassGrowth = end.loadedClassCount - baseline.loadedClassCount;
        int retainedClassLoaders = end.aliveDeploymentClassLoaders;

        assertTrue(
                heapGrowth <= MAX_HEAP_GROWTH_BYTES,
                "Heap growth exceeded bound. baseline=" + baseline.heapUsedBytes +
                        " end=" + end.heapUsedBytes + " growth=" + heapGrowth +
                        " checkpoints=" + checkpoints
        );
        assertTrue(
                loadedClassGrowth <= MAX_LOADED_CLASS_GROWTH,
                "Loaded class growth exceeded bound. baseline=" + baseline.loadedClassCount +
                        " end=" + end.loadedClassCount + " growth=" + loadedClassGrowth +
                        " checkpoints=" + checkpoints
        );
        assertTrue(
                retainedClassLoaders <= MAX_RETAINED_DEPLOYMENT_CLASSLOADERS,
                "Deployment classloaders retained after shutdown. retained=" + retainedClassLoaders +
                        " checkpoints=" + checkpoints
        );
    }

    @Test
    @DisplayName("Repeated setup/shutdown should leave static registries and thread-local backed state clean after each cycle")
    void shouldKeepStaticRegistriesCleanAcrossLifecycles() throws Exception {
        EventImpl.clearStaticState();
        ConversationImpl.clearAllGlobalState();
        ConversationPropagationRegistry.clear();
        DestroyedInstanceTracker.clear();
        resetCounters();

        List<WeakReference<ClassLoader>> deploymentClassLoaders = new ArrayList<WeakReference<ClassLoader>>();
        int cycles = 12;
        for (int i = 0; i < cycles; i++) {
            executeSingleLifecycle(i + 1, deploymentClassLoaders, true);
        }

        forceGcRepeatedly(6);

        assertEquals(cycles, INTERCEPTOR_INVOCATIONS.get(), "Unexpected interceptor invocations after repeated cycles");
        assertEquals(cycles, DECORATOR_INVOCATIONS.get(), "Unexpected decorator invocations after repeated cycles");
        assertEquals(cycles * 2, SYNC_OBSERVER_INVOCATIONS.get(), "Unexpected sync observer invocations after repeated cycles");
        assertEquals(cycles, ASYNC_OBSERVER_INVOCATIONS.get(), "Unexpected async observer invocations after repeated cycles");
        assertTrue(
                countAliveClassLoaders(deploymentClassLoaders) <= MAX_RETAINED_DEPLOYMENT_CLASSLOADERS,
                "Deployment classloaders retained after repeated cycles"
        );
        assertNull(defaultEventExecutor(), "EventImpl async executor should remain cleared between container lifecycles");
    }

    private void executeSingleLifecycle(int cycle,
                                        List<WeakReference<ClassLoader>> deploymentClassLoaders,
                                        boolean verifyCounters) throws Exception {
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        URLClassLoader deploymentTccl = new URLClassLoader(new java.net.URL[0], originalTccl);
        deploymentClassLoaders.add(new WeakReference<ClassLoader>(deploymentTccl));

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SyringeStressLoopMemoryTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        try {
            Thread.currentThread().setContextClassLoader(deploymentTccl);
            syringe.setup();

            BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
            beanManager.getContextManager().activateRequest();
            beanManager.getContextManager().activateSession("stress-session-" + cycle);
            beanManager.getContextManager().beginConversation("stress-conversation-" + cycle);
            try {
                Workflow workflow = resolve(beanManager, Workflow.class);
                String output = workflow.execute("cycle-" + cycle);
                assertTrue(output.startsWith("decorated:"), "Decorator should wrap service result");

                beanManager.getEvent().fire(new SyncPayload(cycle, "direct-fire"));
                beanManager.getEvent().fireAsync(new AsyncPayload(cycle))
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
            } finally {
                beanManager.getContextManager().endConversation("stress-conversation-" + cycle);
                beanManager.getContextManager().deactivateSession();
                beanManager.getContextManager().deactivateRequest();
            }
        } catch (TimeoutException timeoutException) {
            throw new AssertionError("Async observer did not complete within timeout", timeoutException);
        } finally {
            try {
                syringe.shutdown();
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
                closeQuietly(deploymentTccl);
            }
        }

        assertNull(BeanManagerImpl.getRegisteredBeanManager(deploymentTccl), "BeanManager registry leaked deployment classloader");
        assertFalseClientProxyContainerRetains(deploymentTccl);
        assertConversationStateCleared();
        assertNull(ConversationPropagationRegistry.getConversationId(), "Conversation propagation ThreadLocal should be cleared");
        assertNull(defaultEventExecutor(), "EventImpl default async executor should be cleared after shutdown");

        if (verifyCounters) {
            assertTrue(INTERCEPTOR_INVOCATIONS.get() > 0);
            assertTrue(DECORATOR_INVOCATIONS.get() > 0);
            assertTrue(SYNC_OBSERVER_INVOCATIONS.get() > 0);
            assertTrue(ASYNC_OBSERVER_INVOCATIONS.get() > 0);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T resolve(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext creationalContext = beanManager.createCreationalContext(bean);
        return (T) beanManager.getReference(bean, type, creationalContext);
    }

    private static void resetCounters() {
        INTERCEPTOR_INVOCATIONS.set(0);
        DECORATOR_INVOCATIONS.set(0);
        SYNC_OBSERVER_INVOCATIONS.set(0);
        ASYNC_OBSERVER_INVOCATIONS.set(0);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Best-effort.
        }
    }

    private static void forceGcRepeatedly(int iterations) throws InterruptedException {
        for (int i = 0; i < iterations; i++) {
            forceGc();
        }
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(40L);
    }

    private static int countAliveClassLoaders(List<WeakReference<ClassLoader>> deploymentClassLoaders) {
        int alive = 0;
        for (WeakReference<ClassLoader> reference : deploymentClassLoaders) {
            if (reference.get() != null) {
                alive++;
            }
        }
        return alive;
    }

    private static ExecutorService defaultEventExecutor() throws Exception {
        Field field = EventImpl.class.getDeclaredField("defaultAsyncExecutor");
        field.setAccessible(true);
        return (ExecutorService) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static void assertConversationStateCleared() throws Exception {
        Field activeConversations = ConversationImpl.class.getDeclaredField("activeConversations");
        activeConversations.setAccessible(true);
        Map<String, ?> map = (Map<String, ?>) activeConversations.get(null);
        assertTrue(map.isEmpty(), "Conversation global state should be empty after shutdown");
    }

    @SuppressWarnings("unchecked")
    private static void assertFalseClientProxyContainerRetains(ClassLoader deploymentClassLoader) throws Exception {
        Field field = com.threeamigos.common.util.implementations.injection.scopes.ClientProxyGenerator.class
                .getDeclaredField("containerRegistry");
        field.setAccessible(true);
        Map<ClassLoader, ?> registry = (Map<ClassLoader, ?>) field.get(null);
        assertTrue(!registry.containsKey(deploymentClassLoader), "Client proxy container registry leaked classloader");
    }

    private static final class Snapshot {
        private final int cycle;
        private final long heapUsedBytes;
        private final int loadedClassCount;
        private final int aliveDeploymentClassLoaders;

        private Snapshot(int cycle, long heapUsedBytes, int loadedClassCount, int aliveDeploymentClassLoaders) {
            this.cycle = cycle;
            this.heapUsedBytes = heapUsedBytes;
            this.loadedClassCount = loadedClassCount;
            this.aliveDeploymentClassLoaders = aliveDeploymentClassLoaders;
        }

        static Snapshot capture(MemoryMXBean memoryMXBean,
                                ClassLoadingMXBean classLoadingMXBean,
                                List<WeakReference<ClassLoader>> deploymentClassLoaders,
                                int cycle) {
            MemoryUsage usage = memoryMXBean.getHeapMemoryUsage();
            return new Snapshot(
                    cycle,
                    usage.getUsed(),
                    classLoadingMXBean.getLoadedClassCount(),
                    countAliveClassLoaders(deploymentClassLoaders)
            );
        }

        @Override
        public String toString() {
            return "Snapshot{" +
                    "cycle=" + cycle +
                    ", heapUsedBytes=" + heapUsedBytes +
                    ", loadedClassCount=" + loadedClassCount +
                    ", aliveDeploymentClassLoaders=" + aliveDeploymentClassLoaders +
                    '}';
        }
    }

    @InterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    @interface Tracked {
    }

    @Tracked
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 20)
    public static class TrackingInterceptor {
        @AroundInvoke
        public Object aroundInvoke(InvocationContext invocationContext) throws Exception {
            INTERCEPTOR_INVOCATIONS.incrementAndGet();
            return invocationContext.proceed();
        }
    }

    public interface Workflow {
        String execute(String input);
    }

    @ApplicationScoped
    @Tracked
    public static class WorkflowBean implements Workflow {
        @Inject
        private jakarta.enterprise.event.Event<SyncPayload> syncEvent;
        @Inject
        private ConversationProbe conversationProbe;
        @Inject
        private SessionProbe sessionProbe;
        @Inject
        private RequestProbe requestProbe;

        @Override
        public String execute(String input) {
            syncEvent.fire(new SyncPayload(-1, "service-fire"));
            return "service:" + input + ":" + requestProbe.identity() + ":" + sessionProbe.identity() + ":" +
                    conversationProbe.identity();
        }
    }

    @Decorator
    @Priority(Interceptor.Priority.APPLICATION + 40)
    public abstract static class WorkflowDecorator implements Workflow {
        @Inject
        @Delegate
        private Workflow delegate;

        @Override
        public String execute(String input) {
            DECORATOR_INVOCATIONS.incrementAndGet();
            return "decorated:" + delegate.execute(input);
        }
    }

    @ApplicationScoped
    public static class WorkflowObserver {
        void onSync(@Observes SyncPayload payload, RequestProbe requestProbe) {
            SYNC_OBSERVER_INVOCATIONS.incrementAndGet();
            assertNotNull(payload);
            assertNotNull(requestProbe.identity());
        }

        void onAsync(@ObservesAsync AsyncPayload payload, RequestProbe requestProbe) {
            ASYNC_OBSERVER_INVOCATIONS.incrementAndGet();
            assertNotNull(payload);
            assertNotNull(requestProbe.identity());
        }
    }

    public static class SyncPayload {
        private final int cycle;
        private final String source;

        SyncPayload(int cycle, String source) {
            this.cycle = cycle;
            this.source = source;
        }

        public int getCycle() {
            return cycle;
        }

        public String getSource() {
            return source;
        }
    }

    public static class AsyncPayload {
        private final int cycle;

        AsyncPayload(int cycle) {
            this.cycle = cycle;
        }

        public int getCycle() {
            return cycle;
        }
    }

    @RequestScoped
    public static class RequestProbe {
        private final String id = UUID.randomUUID().toString();

        String identity() {
            return id;
        }
    }

    @SessionScoped
    public static class SessionProbe implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id = UUID.randomUUID().toString();

        String identity() {
            return id;
        }
    }

    @ConversationScoped
    public static class ConversationProbe implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id = UUID.randomUUID().toString();

        String identity() {
            return id;
        }
    }

}
