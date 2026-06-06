package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par173dependentpseudoscope;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.3 - Dependent pseudo-scope in CDI full test")
@Isolated
public class DependentPseudoScopeInCDIFullTest {

    @Test
    @DisplayName("17.3 - Instances of decorators are dependent objects of the bean instance they decorate and are destroyed with it")
    void shouldDestroyDecoratorWhenDecoratedDependentBeanIsDestroyed() {
        DecoratorLifecycleRecorder.reset();
        Syringe syringe = newSyringe(DependentDecoratedService.class, TrackingDependentDecorator.class);
        try {
            BeanManager beanManager = syringe.getBeanManager();

            Bean<DependentDecoratedService> bean = resolveBean(beanManager, DependentDecoratedService.class);
            CreationalContext<DependentDecoratedService> context = beanManager.createCreationalContext(bean);
            DependentDecoratedContract instance =
                    (DependentDecoratedContract) beanManager.getReference(bean, DependentDecoratedContract.class, context);

            String response = instance.ping();
            assertTrue(response.startsWith("decorated:"));

            @SuppressWarnings({"rawtypes"})
            Bean rawBean = bean;
            rawBean.destroy(instance, context);

            assertEquals(1, DecoratorLifecycleRecorder.destroyedIds().size());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("17.3 - Each decorated dependent bean instance gets its own dependent decorator instance")
    void shouldCreateDistinctDecoratorInstancesForDistinctDependentBeanInstances() {
        DecoratorLifecycleRecorder.reset();
        Syringe syringe = newSyringe(DependentDecoratedService.class, TrackingDependentDecorator.class);
        try {
            BeanManager beanManager = syringe.getBeanManager();

            Bean<DependentDecoratedService> bean = resolveBean(beanManager, DependentDecoratedService.class);

            CreationalContext<DependentDecoratedService> contextOne = beanManager.createCreationalContext(bean);
            DependentDecoratedContract first =
                    (DependentDecoratedContract) beanManager.getReference(bean, DependentDecoratedContract.class, contextOne);
            String firstDecoratorId = decoratorIdFrom(first.ping());

            CreationalContext<DependentDecoratedService> contextTwo = beanManager.createCreationalContext(bean);
            DependentDecoratedContract second =
                    (DependentDecoratedContract) beanManager.getReference(bean, DependentDecoratedContract.class, contextTwo);
            String secondDecoratorId = decoratorIdFrom(second.ping());

            assertNotEquals(firstDecoratorId, secondDecoratorId);

            @SuppressWarnings({"rawtypes", "unchecked"})
            Bean rawBean = bean;
            rawBean.destroy(first, contextOne);
            rawBean.destroy(second, contextTwo);

            assertTrue(DecoratorLifecycleRecorder.destroyedIds().size() >= 2);
            assertTrue(DecoratorLifecycleRecorder.uniqueDestroyedIds().contains(firstDecoratorId));
            assertTrue(DecoratorLifecycleRecorder.uniqueDestroyedIds().contains(secondDecoratorId));
        } finally {
            syringe.shutdown();
        }
    }

    private String decoratorIdFrom(String value) {
        String[] tokens = value.split(":");
        return tokens.length > 1 ? tokens[1] : "";
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    interface DependentDecoratedContract {
        String ping();
    }

    @Dependent
    public static class DependentDecoratedService implements DependentDecoratedContract {
        @Override
        public String ping() {
            return "bean";
        }
    }

    @Decorator
    @Priority(10)
    public static class TrackingDependentDecorator implements DependentDecoratedContract {
        @Inject
        @Delegate
        DependentDecoratedContract delegate;

        private final String decoratorId = UUID.randomUUID().toString();

        @Override
        public String ping() {
            return "decorated:" + decoratorId + ":" + delegate.ping();
        }

        @PreDestroy
        void preDestroy() {
            DecoratorLifecycleRecorder.recordDestroyed(decoratorId);
        }
    }

    static class DecoratorLifecycleRecorder {
        private static final List<String> DESTROYED_IDS = new CopyOnWriteArrayList<String>();

        static void reset() {
            DESTROYED_IDS.clear();
        }

        static void recordDestroyed(String id) {
            DESTROYED_IDS.add(id);
        }

        static List<String> destroyedIds() {
            return new ArrayList<String>(DESTROYED_IDS);
        }

        static Set<String> uniqueDestroyedIds() {
            return new HashSet<String>(DESTROYED_IDS);
        }
    }
}
