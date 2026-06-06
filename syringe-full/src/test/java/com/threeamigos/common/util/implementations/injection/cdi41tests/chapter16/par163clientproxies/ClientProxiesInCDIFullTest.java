package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter16.par163clientproxies;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("16.3 - Client proxies in CDI full test")
public class ClientProxiesInCDIFullTest {

    @Test
    @DisplayName("16.3 - Normal-scoped bean injected into passivating scoped bean uses a client proxy and passivates even when bean class is not Serializable")
    void shouldUseClientProxyForNormalScopedDependencyInsidePassivatingBean() throws Exception {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SessionBeanWithNormalScopedDependency.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SessionBeanWithNonSerializableDependent.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("16-3-normal-proxy");
        try {
            SessionBeanWithNormalScopedDependency sessionBean =
                    getSessionContextualInstance(beanManager, SessionBeanWithNormalScopedDependency.class);
            assertTrue(sessionBean.isNormalScopedDependencyProxied(),
                    "Normal-scoped dependency inside passivating bean must be injected as a client proxy");
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("16.3 - @Dependent bean is serialized along with its passivating scoped client")
    void shouldSerializeDependentBeanAlongWithPassivatingClient() throws Exception {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SessionBeanWithSerializableDependent.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SessionBeanWithNonSerializableDependent.class);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("16-3-dependent-serialized");
        try {
            SessionBeanWithSerializableDependent sessionBean =
                    getSessionContextualInstance(beanManager, SessionBeanWithSerializableDependent.class);
            sessionBean.incrementCounter();
            sessionBean.incrementCounter();

            SessionBeanWithSerializableDependent passivated = serializeRoundTrip(sessionBean);
            assertNotNull(passivated);
            assertEquals(2, passivated.counter(),
                    "@Dependent state should be serialized with the passivating client");
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("16.3 - Non-serializable @Dependent dependency in passivating scoped bean is a deployment problem")
    void shouldFailForNonSerializableDependentInjectedIntoPassivatingBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SessionBeanWithNonSerializableDependent.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T getSessionContextualInstance(BeanManagerImpl beanManager, Class<T> beanType) {
        BeanManager bm = beanManager;
        Set<Bean<?>> beans = bm.getBeans(beanType);
        Bean bean = bm.resolve((Set) beans);
        CreationalContext creationalContext = bm.createCreationalContext(bean);
        return (T) beanManager.getContext(SessionScoped.class).get((Contextual) bean, creationalContext);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T serializeRoundTrip(T source) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(source);
        oos.flush();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (T) ois.readObject();
    }

    @ApplicationScoped
    public static class NonSerializableApplicationService {
        public String id() {
            return "app-non-serializable";
        }
    }

    @SessionScoped
    public static class SessionBeanWithNormalScopedDependency implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        NonSerializableApplicationService normalScopedService;

        boolean isNormalScopedDependencyProxied() {
            return !NonSerializableApplicationService.class.equals(normalScopedService.getClass());
        }
    }

    @Dependent
    public static class SerializableDependentCounter implements Serializable {
        private static final long serialVersionUID = 1L;
        private int value;

        void increment() {
            value++;
        }

        int value() {
            return value;
        }
    }

    @SessionScoped
    public static class SessionBeanWithSerializableDependent implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        SerializableDependentCounter counter;

        void incrementCounter() {
            counter.increment();
        }

        int counter() {
            return counter.value();
        }
    }

    @Dependent
    public static class NonSerializableDependentCounter {
        private int value;

        void increment() {
            value++;
        }
    }

    @SessionScoped
    public static class SessionBeanWithNonSerializableDependent implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        NonSerializableDependentCounter counter;

        int counter() {
            return counter.value;
        }
    }
}
