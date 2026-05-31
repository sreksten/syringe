package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par61contextualinterface;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("6.1 - Contextual Interface")
public class ContextualInterfaceTest {

    @Test
    @DisplayName("6.1 - Bean (Contextual) create() and destroy() create contextual instances and destroy dependents")
    void shouldCreateAndDestroyContextualInstanceWithDependentObjects() {
        ContextualRecorder.reset();
        Syringe syringe = newSyringe(ContextualParentBean.class, ContextualChildBean.class);

        BeanManager beanManager = syringe.getBeanManager();
        Bean<ContextualParentBean> bean = resolveBean(beanManager, ContextualParentBean.class);
        CreationalContext<ContextualParentBean> context = beanManager.createCreationalContext(bean);
        ContextualParentBean instance =
                (ContextualParentBean) beanManager.getReference(bean, ContextualParentBean.class, context);

        assertNotNull(instance);
        bean.destroy(instance, context);

        List<String> events = ContextualRecorder.events();
        assertTrue(events.contains("parent-pre"));
        assertTrue(events.contains("child-pre"));
    }

    @Test
    @DisplayName("6.1 - Checked exception during create() is wrapped in CreationException")
    void shouldWrapCheckedExceptionThrownDuringCreateInCreationException() {
        Syringe syringe = newSyringe(CheckedFailingCreationBean.class);

        assertThrows(CreationException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                syringe.inject(CheckedFailingCreationBean.class);
            }
        });
    }

    @Test
    @DisplayName("6.1 - Exceptions during destroy() are caught and not propagated")
    void shouldCatchExceptionsDuringDestroy() {
        DestroyFailureRecorder.reset();
        Syringe syringe = newSyringe(DestroyThrowingBean.class);

        BeanManager beanManager = syringe.getBeanManager();
        Bean<DestroyThrowingBean> bean = resolveBean(beanManager, DestroyThrowingBean.class);
        CreationalContext<DestroyThrowingBean> context = beanManager.createCreationalContext(bean);
        DestroyThrowingBean instance =
                (DestroyThrowingBean) beanManager.getReference(bean, DestroyThrowingBean.class, context);

        assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                bean.destroy(instance, context);
            }
        });
        assertEquals(1, DestroyFailureRecorder.preDestroyCalls());
    }

    @Test
    @DisplayName("6.1 - Invocation on contextual instance after destroy is rejected with NonPortableBehaviourException")
    void shouldRejectInvocationAfterDestroyedContextualInstance() {
        Syringe syringe = newSyringe(DestroyableApplicationBean.class);
        DestroyableApplicationBean proxy = syringe.inject(DestroyableApplicationBean.class);
        assertEquals("pong", proxy.ping());

        BeanManager beanManager = syringe.getBeanManager();
        Bean<DestroyableApplicationBean> bean = resolveBean(beanManager, DestroyableApplicationBean.class);
        Context applicationContext = beanManager.getContext(ApplicationScoped.class);
        DestroyableApplicationBean contextualInstance = (DestroyableApplicationBean) applicationContext.get(bean);
        CreationalContext<DestroyableApplicationBean> destroyContext = beanManager.createCreationalContext(bean);
        bean.destroy(contextualInstance, destroyContext);

        assertThrows(NonPortableBehaviourException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                proxy.ping();
            }
        });
    }

    @Test
    @DisplayName("6.1.1 - CreationalContext.push() accepts incompletely initialized instance and create() returns it")
    void shouldAllowPushAndReturnSameIncompleteInstance() {
        Syringe syringe = newSyringe(ContextualParentBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        PushAwareContextual contextual = new PushAwareContextual();
        CreationalContext<PushAwareInstance> creationalContext = beanManager.createCreationalContext(contextual);
        PushAwareInstance created = contextual.create(creationalContext);

        assertNotNull(created);
        assertEquals(created, contextual.getPushedInstance());
    }

    @Test
    @DisplayName("6.1.1 - CreationalContext.release() destroys dependent objects associated with parent instance")
    void shouldDestroyDependentObjectsWhenReleaseIsCalled() {
        ContextualRecorder.reset();
        Syringe syringe = newSyringe(ContextualParentBean.class, ContextualChildBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ContextualParentBean> parentBean = resolveBean(beanManager, ContextualParentBean.class);
        CreationalContext<ContextualParentBean> parentContext = beanManager.createCreationalContext(parentBean);
        ContextualParentBean instance =
                (ContextualParentBean) beanManager.getReference(parentBean, ContextualParentBean.class, parentContext);
        assertNotNull(instance);

        parentContext.release();

        assertTrue(ContextualRecorder.events().contains("child-pre"));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @Dependent
    public static class ContextualParentBean {
        @Inject
        ContextualChildBean child;

        @PreDestroy
        void preDestroy() {
            ContextualRecorder.record("parent-pre");
        }
    }

    @Dependent
    public static class ContextualChildBean {
        @PreDestroy
        void preDestroy() {
            ContextualRecorder.record("child-pre");
        }
    }

    static class ContextualRecorder {
        private static final List<String> EVENTS = new CopyOnWriteArrayList<String>();

        static void reset() {
            EVENTS.clear();
        }

        static void record(String event) {
            EVENTS.add(event);
        }

        static List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    @Dependent
    public static class CheckedFailingCreationBean {
        public CheckedFailingCreationBean() {
            sneakyThrow(new IOException("creation-failure"));
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
            throw (E) throwable;
        }
    }

    @Dependent
    public static class DestroyThrowingBean {
        @PreDestroy
        void preDestroy() {
            DestroyFailureRecorder.increment();
            throw new IllegalStateException("destroy-failure");
        }
    }

    static class DestroyFailureRecorder {
        private static int preDestroyCalls = 0;

        static void reset() {
            preDestroyCalls = 0;
        }

        static void increment() {
            preDestroyCalls++;
        }

        static int preDestroyCalls() {
            return preDestroyCalls;
        }
    }

    @ApplicationScoped
    public static class DestroyableApplicationBean {
        public String ping() {
            return "pong";
        }
    }

    static class PushAwareInstance {
    }

    static class PushAwareContextual implements Contextual<PushAwareInstance> {
        private PushAwareInstance pushedInstance;

        @Override
        public PushAwareInstance create(CreationalContext<PushAwareInstance> creationalContext) {
            PushAwareInstance instance = new PushAwareInstance();
            creationalContext.push(instance);
            this.pushedInstance = instance;
            return instance;
        }

        @Override
        public void destroy(PushAwareInstance instance, CreationalContext<PushAwareInstance> creationalContext) {
            creationalContext.release();
        }

        PushAwareInstance getPushedInstance() {
            return pushedInstance;
        }
    }
}
