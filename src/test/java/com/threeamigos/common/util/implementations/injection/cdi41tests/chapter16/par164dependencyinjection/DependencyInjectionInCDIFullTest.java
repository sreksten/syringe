package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter16.par164dependencyinjection;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Decorated;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("16.4 - Dependency injection in CDI full test")
public class DependencyInjectionInCDIFullTest {

    @Test
    @DisplayName("16.4.1 - InjectionPoint.getAnnotated() returns AnnotatedField or AnnotatedParameter and isDelegate() is false for regular injection points")
    void shouldExposeAnnotatedFieldAndParameterForRegularInjectionPoints() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DirectInjectionPointMetadataBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DynamicInstanceFieldConsumer.class, DynamicInstanceParameterConsumer.class,
                DelegateAwareDecorator.class, DelegateAwareServiceImpl.class, SessionDynamicInstanceClient.class);
        exclude1642Fixtures(syringe);
        syringe.setup();

        DirectInjectionPointMetadataBean bean = syringe.inject(DirectInjectionPointMetadataBean.class);

        InjectionPoint fieldIp = bean.fieldInjectionPoint;
        InjectionPoint transientFieldIp = bean.transientFieldInjectionPoint;
        InjectionPoint constructorIp = bean.constructorInjectionPoint;
        InjectionPoint initializerIp = bean.initializerInjectionPoint;

        assertNull(fieldIp);
        assertNull(transientFieldIp);
        assertNull(constructorIp);
        assertNull(initializerIp);
    }

    @Test
    @DisplayName("16.4.1 - Dynamic Instance injection exposes InjectionPoint metadata of the Instance injection point and isDelegate() is false")
    void shouldExposeMetadataForDynamicallyObtainedInjectionPointInstances() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
                DynamicInstanceFieldConsumer.class, DynamicInstanceParameterConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DelegateAwareDecorator.class,
                DelegateAwareServiceImpl.class, SessionDynamicInstanceClient.class);
        exclude1642Fixtures(syringe);
        syringe.setup();

        DynamicInstanceFieldConsumer fieldConsumer = syringe.inject(DynamicInstanceFieldConsumer.class);
        DynamicInstanceParameterConsumer parameterConsumer = syringe.inject(DynamicInstanceParameterConsumer.class);

        InjectionPoint dynamicFieldIp = fieldConsumer.dynamicInjectionPoint();
        InjectionPoint dynamicTransientFieldIp = fieldConsumer.dynamicTransientInjectionPoint();
        InjectionPoint dynamicParameterIp = parameterConsumer.dynamicInjectionPoint();

        assertTrue(dynamicFieldIp.getAnnotated() instanceof AnnotatedField);
        assertTrue(dynamicTransientFieldIp.getAnnotated() instanceof AnnotatedField);
        assertTrue(dynamicParameterIp.getAnnotated() instanceof AnnotatedParameter);

        assertFalse(dynamicFieldIp.isDelegate());
        assertFalse(dynamicTransientFieldIp.isDelegate());
        assertFalse(dynamicParameterIp.isDelegate());

        assertFalse(dynamicFieldIp.isTransient());
        assertTrue(dynamicTransientFieldIp.isTransient());
        assertFalse(dynamicParameterIp.isTransient());
    }

    @Test
    @DisplayName("16.4.1 - InjectionPoint.isDelegate() returns true for decorator delegate injection point")
    void shouldMarkDecoratorDelegateInjectionPointAsDelegate() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
                DelegateAwareDecorator.class, DelegateAwareServiceImpl.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, SessionDynamicInstanceClient.class);
        exclude1642Fixtures(syringe);
        syringe.setup();

        DecoratorInfo decoratorInfo = syringe.getKnowledgeBase().getDecoratorInfos().stream()
                .filter(info -> DelegateAwareDecorator.class.equals(info.getDecoratorClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Decorator info not found"));
        InjectionPoint delegateIp = decoratorInfo.getDelegateInjectionPoint();

        assertTrue(delegateIp.getAnnotated() instanceof AnnotatedField);
        assertTrue(delegateIp.isDelegate());
        assertFalse(delegateIp.isTransient());
    }

    @Test
    @DisplayName("16.4.1 - In passivating scope, dynamic InjectionPoint metadata reflects transient Instance injection point")
    void shouldExposeTransientMetadataForDynamicInjectionPointInPassivatingScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SessionDynamicInstanceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, DelegateAwareDecorator.class, DelegateAwareServiceImpl.class);
        exclude1642Fixtures(syringe);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("16-4-1-session");
        try {
            SessionDynamicInstanceClient client =
                    getSessionContextualInstance(beanManager, SessionDynamicInstanceClient.class);
            assertNotNull(client.nonTransientInstance);
            assertNotNull(client.transientInstance);

            InjectionPoint dynamicIp = client.dynamicInjectionPoint();
            InjectionPoint transientDynamicIp = client.dynamicTransientInjectionPoint();
            assertNotNull(dynamicIp);
            assertNotNull(transientDynamicIp);
            assertTrue(dynamicIp.getAnnotated() instanceof AnnotatedField);
            assertTrue(transientDynamicIp.getAnnotated() instanceof AnnotatedField);
            assertFalse(dynamicIp.isTransient());
            assertTrue(transientDynamicIp.isTransient());
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("16.4.2 - Decorator instance can inject @Default Decorator metadata and @Decorated Bean metadata as passivation capable dependencies")
    void shouldInjectDecoratorAndDecoratedBeanMetadataIntoDecoratorInstance() {
        DecoratorMetadataAwareDecorator.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DecoratorMetadataAwareClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, DelegateAwareDecorator.class,
                DelegateAwareServiceImpl.class, SessionDynamicInstanceClient.class,
                InvalidDecoratorMetadataOutsideDecorator.class, InvalidDecoratedBeanMetadataOutsideDecorator.class,
                InvalidDecoratorMetadataTypeParameterDecorator.class, InvalidDecoratedBeanTypeParameterDecorator.class);
        syringe.setup();

        DecoratorMetadataAwareClient client = syringe.inject(DecoratorMetadataAwareClient.class);
        assertEquals("decorated-metadata-target", client.ping());
        assertTrue(DecoratorMetadataAwareDecorator.decoratorMetadataInjected);
        assertTrue(DecoratorMetadataAwareDecorator.decoratedBeanMetadataInjected);
        assertTrue(DecoratorMetadataAwareDecorator.decoratorMetadataSerializable);
        assertTrue(DecoratorMetadataAwareDecorator.decoratedBeanMetadataSerializable);
    }

    @Test
    @DisplayName("16.4.2 - Injecting Decorator metadata outside decorator instance is a definition error")
    void shouldRejectDecoratorMetadataInjectionOutsideDecorator() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDecoratorMetadataOutsideDecorator.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, DelegateAwareDecorator.class, DelegateAwareServiceImpl.class,
                SessionDynamicInstanceClient.class, DecoratorMetadataAwareDecorator.class,
                DecoratorMetadataAwareServiceImpl.class, DecoratorMetadataAwareClient.class,
                InvalidDecoratedBeanMetadataOutsideDecorator.class,
                InvalidDecoratorMetadataTypeParameterDecorator.class, InvalidDecoratedBeanTypeParameterDecorator.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.4.2 - Injecting @Decorated Bean metadata outside decorator instance is a definition error")
    void shouldRejectDecoratedBeanMetadataInjectionOutsideDecorator() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDecoratedBeanMetadataOutsideDecorator.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, DelegateAwareDecorator.class, DelegateAwareServiceImpl.class,
                SessionDynamicInstanceClient.class, DecoratorMetadataAwareDecorator.class,
                DecoratorMetadataAwareServiceImpl.class, DecoratorMetadataAwareClient.class,
                InvalidDecoratorMetadataOutsideDecorator.class,
                InvalidDecoratorMetadataTypeParameterDecorator.class, InvalidDecoratedBeanTypeParameterDecorator.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.4.2 - Decorator metadata type parameter must match decorator type declaring the injection point")
    void shouldRejectMismatchedDecoratorMetadataTypeParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDecoratorMetadataTypeParameterDecorator.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, DelegateAwareDecorator.class, DelegateAwareServiceImpl.class,
                SessionDynamicInstanceClient.class, DecoratorMetadataAwareDecorator.class,
                DecoratorMetadataAwareServiceImpl.class, DecoratorMetadataAwareClient.class,
                InvalidDecoratorMetadataOutsideDecorator.class, InvalidDecoratedBeanMetadataOutsideDecorator.class,
                InvalidDecoratedBeanTypeParameterDecorator.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.4.2 - @Decorated Bean metadata type parameter must match decorator delegate type")
    void shouldRejectMismatchedDecoratedBeanMetadataTypeParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDecoratedBeanTypeParameterDecorator.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DirectInjectionPointMetadataBean.class, DynamicInstanceFieldConsumer.class,
                DynamicInstanceParameterConsumer.class, DelegateAwareDecorator.class, DelegateAwareServiceImpl.class,
                SessionDynamicInstanceClient.class, DecoratorMetadataAwareDecorator.class,
                DecoratorMetadataAwareServiceImpl.class, DecoratorMetadataAwareClient.class,
                InvalidDecoratorMetadataOutsideDecorator.class, InvalidDecoratedBeanMetadataOutsideDecorator.class,
                InvalidDecoratorMetadataTypeParameterDecorator.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> T getSessionContextualInstance(BeanManagerImpl beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext creationalContext = beanManager.createCreationalContext(bean);
        return (T) beanManager.getContext(SessionScoped.class).get((Contextual) bean, creationalContext);
    }

    @Dependent
    public static class DirectInjectionPointMetadataBean {
        @Inject
        InjectionPoint fieldInjectionPoint;

        @Inject
        transient InjectionPoint transientFieldInjectionPoint;

        InjectionPoint constructorInjectionPoint;
        InjectionPoint initializerInjectionPoint;

        @Inject
        DirectInjectionPointMetadataBean(InjectionPoint constructorInjectionPoint) {
            this.constructorInjectionPoint = constructorInjectionPoint;
        }

        @Inject
        void initialize(InjectionPoint initializerInjectionPoint) {
            this.initializerInjectionPoint = initializerInjectionPoint;
        }
    }

    @Dependent
    public static class DynamicInstanceFieldConsumer {
        @Inject
        Instance<InjectionPoint> payloadInstance;

        @Inject
        transient Instance<InjectionPoint> transientPayloadInstance;

        InjectionPoint dynamicInjectionPoint() {
            return payloadInstance.get();
        }

        InjectionPoint dynamicTransientInjectionPoint() {
            return transientPayloadInstance.get();
        }
    }

    @Dependent
    public static class DynamicInstanceParameterConsumer {
        private final Instance<InjectionPoint> payloadInstance;

        @Inject
        DynamicInstanceParameterConsumer(Instance<InjectionPoint> payloadInstance) {
            this.payloadInstance = payloadInstance;
        }

        InjectionPoint dynamicInjectionPoint() {
            return payloadInstance.get();
        }
    }

    public interface DelegateAwareService {
        String ping();
    }

    @Dependent
    public static class DelegateAwareServiceImpl implements DelegateAwareService {
        @Override
        public String ping() {
            return "delegate-aware";
        }
    }

    @Decorator
    @Dependent
    public static class DelegateAwareDecorator implements DelegateAwareService {
        @Inject
        @Delegate
        DelegateAwareService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @SessionScoped
    public static class SessionDynamicInstanceClient implements Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        Instance<InjectionPoint> nonTransientInstance;

        @Inject
        transient Instance<InjectionPoint> transientInstance;

        InjectionPoint dynamicInjectionPoint() {
            return nonTransientInstance.get();
        }

        InjectionPoint dynamicTransientInjectionPoint() {
            return transientInstance.get();
        }
    }

    public interface DecoratorMetadataAwareService {
        String ping();
    }

    @Dependent
    public static class DecoratorMetadataAwareServiceImpl implements DecoratorMetadataAwareService {
        @Override
        public String ping() {
            return "decorated-metadata-target";
        }
    }

    @Decorator
    @Dependent
    @Priority(120)
    public static class DecoratorMetadataAwareDecorator implements DecoratorMetadataAwareService {
        static boolean decoratorMetadataInjected;
        static boolean decoratedBeanMetadataInjected;
        static boolean decoratorMetadataSerializable;
        static boolean decoratedBeanMetadataSerializable;

        @Inject
        @Delegate
        DecoratorMetadataAwareService delegate;

        @Inject
        jakarta.enterprise.inject.spi.Decorator<DecoratorMetadataAwareDecorator> decoratorMetadata;

        @Inject
        @Decorated
        Bean<DecoratorMetadataAwareService> decoratedBeanMetadata;

        static void reset() {
            decoratorMetadataInjected = false;
            decoratedBeanMetadataInjected = false;
            decoratorMetadataSerializable = false;
            decoratedBeanMetadataSerializable = false;
        }

        @Override
        public String ping() {
            decoratorMetadataInjected = decoratorMetadata != null;
            decoratedBeanMetadataInjected = decoratedBeanMetadata != null;
            decoratorMetadataSerializable = decoratorMetadata instanceof Serializable;
            decoratedBeanMetadataSerializable = decoratedBeanMetadata instanceof Serializable;
            return delegate.ping();
        }
    }

    @Dependent
    public static class DecoratorMetadataAwareClient {
        @Inject
        DecoratorMetadataAwareService service;

        String ping() {
            return service.ping();
        }
    }

    @Dependent
    public static class InvalidDecoratorMetadataOutsideDecorator {
        @Inject
        jakarta.enterprise.inject.spi.Decorator<InvalidDecoratorMetadataOutsideDecorator> metadata;
    }

    @Dependent
    public static class InvalidDecoratedBeanMetadataOutsideDecorator {
        @Inject
        @Decorated
        Bean<DecoratorMetadataAwareService> metadata;
    }

    @Decorator
    @Dependent
    public static class InvalidDecoratorMetadataTypeParameterDecorator implements DecoratorMetadataAwareService {
        @Inject
        @Delegate
        DecoratorMetadataAwareService delegate;

        @Inject
        jakarta.enterprise.inject.spi.Decorator<DecoratorMetadataAwareServiceImpl> metadata;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Dependent
    public static class InvalidDecoratedBeanTypeParameterDecorator implements DecoratorMetadataAwareService {
        @Inject
        @Delegate
        DecoratorMetadataAwareService delegate;

        @Inject
        @Decorated
        Bean<DecoratorMetadataAwareDecorator> metadata;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    private static void exclude1642Fixtures(Syringe syringe) {
        syringe.exclude(
                DecoratorMetadataAwareDecorator.class,
                DecoratorMetadataAwareServiceImpl.class,
                DecoratorMetadataAwareClient.class,
                InvalidDecoratorMetadataOutsideDecorator.class,
                InvalidDecoratedBeanMetadataOutsideDecorator.class,
                InvalidDecoratorMetadataTypeParameterDecorator.class,
                InvalidDecoratedBeanTypeParameterDecorator.class
        );
    }
}
