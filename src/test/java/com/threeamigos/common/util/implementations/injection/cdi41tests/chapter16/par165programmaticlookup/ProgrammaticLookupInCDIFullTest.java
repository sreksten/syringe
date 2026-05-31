package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter16.par165programmaticlookup;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("16.5 - Programmatic lookup in CDI full test")
public class ProgrammaticLookupInCDIFullTest {

    @Test
    @DisplayName("16.5 - Built-in Instance implementation is a passivation capable dependency")
    void shouldTreatBuiltInInstanceAsPassivationCapableDependency() throws Exception {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SessionInstanceHolder.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("16-5-instance-passivation");
        try {
            SessionInstanceHolder holder = getSessionContextualInstance(beanManager, SessionInstanceHolder.class);
            assertNotNull(holder.instance);
            assertNotNull(holder.lookupPayload());

            SessionInstanceHolder passivated = serializeRoundTrip(holder);
            assertNotNull(passivated);
            assertNotNull(passivated.instance);
            assertNotNull(passivated.lookupPayload());
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T getSessionContextualInstance(BeanManagerImpl beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext creationalContext = beanManager.createCreationalContext(bean);
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

    @Dependent
    public static class LookupPayload implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @SessionScoped
    public static class SessionInstanceHolder implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        Instance<LookupPayload> instance;

        LookupPayload lookupPayload() {
            return instance.get();
        }
    }
}
