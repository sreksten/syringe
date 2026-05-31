package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.tckparity.builtinpassivation;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.5.3 - TCK parity for built-in passivation capable dependencies")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BuiltinBeanPassivationDependencyTckParityTest {

    @Test
    @DisplayName("17.5.3 / BuiltinBeanPassivationDependencyTest - Instance built-in dependency remains usable after passivation")
    void shouldMatchBuiltinBeanPassivationDependencyTestForInstance() throws Exception {
        Syringe syringe = newSyringe(Worker.class, Hammer.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("builtin-instance-session");
        beanManager.getContextManager().activateRequest();
        try {
            Worker worker = getSessionScopedInstance(beanManager, Worker.class);
            String workerId = worker.getId();
            String hammerId = worker.getInstance().get().getId();

            Worker copy = deserialize(serialize(worker));
            assertNotNull(copy);
            assertNotNull(copy.getInstance());
            assertEquals(workerId, copy.getId());
            assertEquals(hammerId, copy.getInstance().get().getId());
        } finally {
            beanManager.getContextManager().deactivateRequest();
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("17.5.3 / BuiltinBeanPassivationDependencyTest - BeanManager built-in dependency remains usable after passivation")
    void shouldMatchBuiltinBeanPassivationDependencyTestForBeanManager() throws Exception {
        Syringe syringe = newSyringe(Boss.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("builtin-beanmanager-session");
        try {
            Boss boss = getSessionScopedInstance(beanManager, Boss.class);
            String bossId = boss.getId();

            Boss copy = deserialize(serialize(boss));
            assertNotNull(copy);
            assertNotNull(copy.getBeanManager());
            assertEquals(bossId, copy.getId());
            assertEquals(1, copy.getBeanManager().getBeans(Boss.class).size());
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getSessionScopedInstance(BeanManagerImpl beanManager, Class<T> type) {
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beanManager.getBeans(type));
        Context sessionContext = beanManager.getContext(SessionScoped.class);
        return sessionContext.get(bean, beanManager.createCreationalContext(bean));
    }

    @Test
    @DisplayName("17.5.3 / BuiltinBeanPassivationDependencyTest - InjectionPoint built-in dependency remains usable after passivation")
    void shouldMatchBuiltinBeanPassivationDependencyTestForInjectionPoint() throws Exception {
        Syringe syringe = newSyringe(Inspector.class, InspectorAssistant.class);

        Inspector inspector = syringe.inject(InspectorAssistant.class).getInspector();
        String inspectorId = inspector.getId();
        InjectionPoint originalInjectionPoint = inspector.getInjectionPoint();

        Inspector copy = deserialize(serialize(inspector));
        assertNotNull(copy);
        assertNotNull(copy.getInjectionPoint());
        assertEquals(inspectorId, copy.getId());
        assertEquals(originalInjectionPoint.getType(), copy.getInjectionPoint().getType());
        assertEquals(originalInjectionPoint.getQualifiers(), copy.getInjectionPoint().getQualifiers());
        assertEquals(originalInjectionPoint.getBean(), copy.getInjectionPoint().getBean());
        assertEquals(originalInjectionPoint.getMember(), copy.getInjectionPoint().getMember());
        assertEquals(
                unwrapAnnotated(copy.getInjectionPoint().getAnnotated()),
                unwrapAnnotated(originalInjectionPoint.getAnnotated()));
        assertTrue(copy.getInjectionPoint().getAnnotated().getBaseType().equals(originalInjectionPoint.getAnnotated().getBaseType()));
        assertEquals(copy.getInjectionPoint().getAnnotated().getAnnotations(), originalInjectionPoint.getAnnotated().getAnnotations());
    }

    private Object unwrapAnnotated(Annotated annotated) {
        if (annotated instanceof AnnotatedMember) {
            return ((AnnotatedMember<?>) annotated).getJavaMember();
        }
        if (annotated instanceof AnnotatedParameter) {
            return ((AnnotatedParameter<?>) annotated).getJavaParameter();
        }
        if (annotated instanceof AnnotatedType) {
            return ((AnnotatedType<?>) annotated).getJavaClass();
        }
        throw new UnsupportedOperationException("Unknown Annotated instance: " + annotated);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(value);
        oos.flush();
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
        return (T) ois.readObject();
    }

    @SessionScoped
    public static class Worker implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;

        @Inject
        private Instance<Hammer> instance;

        @PostConstruct
        void init() {
            id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }

        public Instance<Hammer> getInstance() {
            return instance;
        }
    }

    @RequestScoped
    public static class Hammer {
        private String id;

        @PostConstruct
        void init() {
            id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }
    }

    @SessionScoped
    public static class Boss implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;

        @Inject
        private BeanManager beanManager;

        @PostConstruct
        void init() {
            id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }

        public BeanManager getBeanManager() {
            return beanManager;
        }
    }

    @Dependent
    public static class Inspector implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;

        @Inject
        private InjectionPoint injectionPoint;

        @PostConstruct
        void init() {
            id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }

        public InjectionPoint getInjectionPoint() {
            return injectionPoint;
        }
    }

    @Dependent
    public static class InspectorAssistant {
        @Inject
        private Inspector inspector;

        public Inspector getInspector() {
            return inspector;
        }
    }
}
