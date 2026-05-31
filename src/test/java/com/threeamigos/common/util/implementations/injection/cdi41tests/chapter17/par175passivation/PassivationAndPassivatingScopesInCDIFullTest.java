package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.scopes.SessionScopedContext;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.TransientReference;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;
import jakarta.annotation.Priority;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.5 - Passivation and passivating scopes in CDI full test")
public class PassivationAndPassivatingScopesInCDIFullTest {
    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            PassivationAnchor.class,
            SerializableSessionBean.class,
            NonSerializableSessionBean.class,
            ProducerMethodSwitch.class,
            ProducerFieldSwitch.class,
            SessionBeanWithMethodProducedDependency.class,
            SessionBeanWithFieldProducedDependency.class,
            SerializableMethodProducer.class,
            NonSerializableMethodProducer.class,
            SessionBeanWithSerializableMethodProduct.class,
            SessionBeanWithNonSerializableMethodProduct.class,
            SerializableFieldProducer.class,
            NonSerializableFieldProducer.class,
            SessionBeanWithSerializableFieldProduct.class,
            SessionBeanWithNonSerializableFieldProduct.class,
            SerializablePayload.class,
            NonSerializablePayload.class,
            NonSerializableDependentPlain.class,
            SerializableDependent.class,
            NonSerializableNormalService.class,
            SessionBeanWithTransientFieldDependency.class,
            SessionBeanWithPassivationCapableFieldDependency.class,
            SessionBeanWithTransientReferenceConstructorDependency.class,
            SessionBeanWithPassivationCapableConstructorDependency.class,
            SessionBeanWithTransientReferenceMethodDependency.class,
            SessionBeanWithPassivationCapableMethodDependency.class,
            SessionBeanWithNormalScopedDependency.class,
            SessionBeanWithDependentPassivationCapableDependency.class,
            SessionBeanWithInstanceBuiltInDependency.class,
            SessionBeanWithEventBuiltInDependency.class,
            SessionBeanWithDependentInjectionPointBuiltInDependency.class,
            DependentWithInjectionPointMetadata.class,
            SessionBeanWithBeanManagerBuiltInDependency.class,
            SessionBeanWithCustomPassivationCapableDependency.class,
            SerializableCustomPassivatingScopedBean.class,
            NonSerializableCustomPassivatingScopedBean.class,
            SessionBeanWithNonPassivationCapableDependency.class,
            SessionBeanWithNonPassivationCapableConstructorDependency.class,
            SessionBeanWithNonPassivationCapableInitializerDependency.class,
            InvalidPassivatingProducerMethodHolder.class,
            InvalidPassivatingProducerFieldHolder.class,
            RuntimeInvalidPassivatingProducerMethodHolder.class,
            RuntimeInvalidPassivatingProducerFieldHolder.class,
            SessionBeanWithRuntimeInvalidMethodProduct.class,
            SessionBeanWithRuntimeInvalidFieldProduct.class,
            DependentRuntimeInvalidProducerHolder.class,
            SessionBeanWithDependentRuntimeInvalidProduct.class,
            SessionBeanWithNonSerializableInterceptor.class,
            NonSerializableInterceptor.class,
            SessionBeanWithInterceptorHavingNonPassivationCapableInjectionPoint.class,
            SerializableInterceptorWithNonSerializableInjectionPoint.class,
            DecoratedService.class,
            SessionBeanWithNonSerializableDecorator.class,
            NonSerializableDecorator.class,
            SessionBeanWithDecoratorHavingNonPassivationCapableInjectionPoint.class,
            SerializableDecoratorWithNonSerializableInjectionPoint.class,
            CustomSessionScopedSyntheticBeanService.class
    };

    @Test
    @DisplayName("17.5.1 - A managed bean is passivation capable iff its bean class is serializable")
    void shouldRequireManagedBeanClassToBeSerializableInPassivatingScope() {
        Syringe valid = newSyringe(SerializableSessionBean.class);
        assertDoesNotThrow(valid::setup);

        Syringe invalid = newSyringe(NonSerializableSessionBean.class);
        assertThrows(DeploymentException.class, invalid::setup);
    }

    @Test
    @DisplayName("17.5.1 - A producer method is passivation capable only if it never returns non-passivation-capable values at runtime")
    void shouldFailSessionPassivationWhenProducerMethodReturnsNonSerializableValue() {
        ProducerMethodSwitch.useSerializableValue = false;
        Syringe syringe = newSyringe(
                ProducerMethodSwitch.class,
                SessionBeanWithMethodProducedDependency.class
        );
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("17-5-1-method-fail-runtime");
        try {
            RuntimeException runtimeException = assertThrows(RuntimeException.class,
                    () -> getSessionBean(beanManager, SessionBeanWithMethodProducedDependency.class));
            assertTrue(containsCause(runtimeException, IllegalProductException.class));
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("17.5.1 - A producer method is passivation capable when it only produces passivation-capable values")
    void shouldAllowSessionBeanUsingSerializableProducerMethodProduct() {
        Syringe valid = newSyringe(
                SerializableMethodProducer.class,
                SessionBeanWithSerializableMethodProduct.class
        );
        assertDoesNotThrow(valid::setup);
    }

    @Test
    @DisplayName("17.5.1 - A producer field is passivation capable only if it never refers to non-passivation-capable values at runtime")
    void shouldFailSessionPassivationWhenProducerFieldRefersToNonSerializableValue() {
        ProducerFieldSwitch.current = new NonSerializablePayload();
        Syringe syringe = newSyringe(
                ProducerFieldSwitch.class,
                SessionBeanWithFieldProducedDependency.class
        );
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("17-5-1-field-fail-runtime");
        try {
            RuntimeException runtimeException = assertThrows(RuntimeException.class,
                    () -> getSessionBean(beanManager, SessionBeanWithFieldProducedDependency.class));
            assertTrue(containsCause(runtimeException, IllegalProductException.class));
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("17.5.1 - A producer field is passivation capable when it only refers to passivation-capable values")
    void shouldAllowSessionBeanUsingSerializableProducerFieldProduct() {
        Syringe valid = newSyringe(
                SerializableFieldProducer.class,
                SessionBeanWithSerializableFieldProduct.class
        );
        assertDoesNotThrow(valid::setup);
    }

    @Test
    @DisplayName("17.5.1 - A custom Bean implementation is passivation capable when it implements PassivationCapable")
    void shouldResolveCustomPassivationCapableBeanById() {
        CustomPassivationCapableBean customBean = new CustomPassivationCapableBean();
        Syringe syringe = newSyringe(PassivationAnchor.class);
        syringe.getKnowledgeBase().addBean(customBean);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> resolved = beanManager.getPassivationCapableBean(customBean.getId());

        assertNotNull(resolved);
        assertEquals(customBean.getId(), ((PassivationCapable) resolved).getId());
        assertTrue(customBean.getId().contains(customBean.getClass().getPackage().getName()));
    }

    @Test
    @DisplayName("17.5.1 - A non-bean Contextual is passivation capable when it implements PassivationCapable and Serializable")
    void shouldTreatNonBeanContextualAsPassivationCapableWhenItImplementsBothContracts() {
        NonBeanPassivationCapableContextual one = new NonBeanPassivationCapableContextual();
        NonBeanPassivationCapableContextual two = new NonBeanPassivationCapableContextual();

        assertTrue(one instanceof PassivationCapable);
        assertTrue(one instanceof Serializable);
        assertNotEquals(one.getId(), two.getId());
        assertTrue(one.getId().contains(one.getClass().getPackage().getName()));
    }

    @Test
    @DisplayName("17.5.2 - A transient field injection point is passivation capable")
    void shouldTreatTransientFieldInjectionPointAsPassivationCapable() {
        Syringe syringe = newSyringe(
                SessionBeanWithTransientFieldDependency.class,
                NonSerializableDependentPlain.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.2 - A non-transient field injection point is passivation capable when it resolves to a passivation capable dependency")
    void shouldTreatNonTransientFieldAsPassivationCapableWhenDependencyIsPassivationCapable() {
        Syringe syringe = newSyringe(
                SessionBeanWithPassivationCapableFieldDependency.class,
                NonSerializableNormalService.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.2 - A constructor parameter annotated with @TransientReference is a passivation capable injection point")
    void shouldTreatTransientReferenceConstructorParameterAsPassivationCapable() {
        Syringe syringe = newSyringe(
                SessionBeanWithTransientReferenceConstructorDependency.class,
                NonSerializableDependentPlain.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.2 - A constructor parameter is passivation capable when it resolves to a passivation capable dependency")
    void shouldTreatConstructorParameterAsPassivationCapableWhenDependencyIsPassivationCapable() {
        Syringe syringe = newSyringe(
                SessionBeanWithPassivationCapableConstructorDependency.class,
                SerializableDependent.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.2 - A method parameter annotated with @TransientReference is a passivation capable injection point")
    void shouldTreatTransientReferenceMethodParameterAsPassivationCapable() {
        Syringe syringe = newSyringe(
                SessionBeanWithTransientReferenceMethodDependency.class,
                NonSerializableDependentPlain.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.2 - A method parameter is passivation capable when it resolves to a passivation capable dependency")
    void shouldTreatMethodParameterAsPassivationCapableWhenDependencyIsPassivationCapable() {
        Syringe syringe = newSyringe(
                SessionBeanWithPassivationCapableMethodDependency.class,
                SerializableDependent.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - All beans with normal scope are passivation capable dependencies")
    void shouldTreatNormalScopedBeansAsPassivationCapableDependencies() {
        Syringe syringe = newSyringe(
                SessionBeanWithNormalScopedDependency.class,
                NonSerializableNormalService.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - All passivation capable @Dependent beans are passivation capable dependencies")
    void shouldTreatSerializableDependentBeansAsPassivationCapableDependencies() {
        Syringe syringe = newSyringe(
                SessionBeanWithDependentPassivationCapableDependency.class,
                SerializableDependent.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - Built-in bean Instance is a passivation capable dependency")
    void shouldTreatBuiltInInstanceAsPassivationCapableDependency() {
        Syringe syringe = newSyringe(
                SessionBeanWithInstanceBuiltInDependency.class,
                NonSerializableNormalService.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - Built-in bean Event is a passivation capable dependency")
    void shouldTreatBuiltInEventAsPassivationCapableDependency() {
        Syringe syringe = newSyringe(SessionBeanWithEventBuiltInDependency.class);
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - Built-in bean InjectionPoint is a passivation capable dependency")
    void shouldTreatBuiltInInjectionPointAsPassivationCapableDependency() {
        Syringe syringe = newSyringe(
                SessionBeanWithDependentInjectionPointBuiltInDependency.class,
                DependentWithInjectionPointMetadata.class
        );
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - Built-in bean BeanManager is a passivation capable dependency")
    void shouldTreatBuiltInBeanManagerAsPassivationCapableDependency() {
        Syringe syringe = newSyringe(SessionBeanWithBeanManagerBuiltInDependency.class);
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.3 - A custom Bean implementation is a passivation capable dependency when it implements PassivationCapable")
    void shouldTreatCustomPassivationCapableBeanAsPassivationCapableDependency() {
        Syringe syringe = newSyringe(
                PassivationAnchor.class,
                SessionBeanWithCustomPassivationCapableDependency.class
        );
        syringe.getKnowledgeBase().addBean(new CustomPassivationCapableDependencyBean());
        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("17.5.4 - Built-in passivating scopes are only session and conversation")
    void shouldReportOnlySessionAndConversationAsBuiltInPassivatingScopes() {
        Syringe syringe = newSyringe(PassivationAnchor.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(beanManager.isPassivatingScope(SessionScoped.class));
        assertTrue(beanManager.isPassivatingScope(ConversationScoped.class));
        assertTrue(!beanManager.isPassivatingScope(ApplicationScoped.class));
        assertTrue(!beanManager.isPassivatingScope(RequestScoped.class));
        assertTrue(!beanManager.isPassivatingScope(Dependent.class));
    }

    @Test
    @DisplayName("17.5.4 - A passivating scope must be explicitly declared with @NormalScope(passivating=true)")
    void shouldRequirePassivatingScopeDeclarationOnNormalScopeAnnotation() {
        NormalScope normalScope = CustomPassivatingScope.class.getAnnotation(NormalScope.class);
        assertNotNull(normalScope);
        assertTrue(normalScope.passivating());
    }

    @Test
    @DisplayName("17.5.4 - Beans with a passivating scope must be passivation capable")
    void shouldRequireBeansWithCustomPassivatingScopeToBePassivationCapable() {
        Syringe valid = newSyringe(SerializableCustomPassivatingScopedBean.class);
        valid.registerCustomContext(CustomPassivatingScope.class, new RecordingPassivatingContext());
        assertDoesNotThrow(valid::setup);

        Syringe invalid = newSyringe(NonSerializableCustomPassivatingScopedBean.class);
        invalid.registerCustomContext(CustomPassivatingScope.class, new RecordingPassivatingContext());
        assertThrows(DeploymentException.class, invalid::setup);
    }

    @Test
    @DisplayName("17.5.4 - Contextual implementations passed to passivating scope contexts must be passivation capable")
    void shouldPassPassivationCapableContextualToPassivatingScopeContext() {
        RecordingPassivatingContext.reset();

        Syringe syringe = newSyringe(SerializableCustomPassivatingScopedBean.class);
        syringe.registerCustomContext(CustomPassivatingScope.class, new RecordingPassivatingContext());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(SerializableCustomPassivatingScopedBean.class);
        Bean<?> bean = beanManager.resolve(beans);
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        SerializableCustomPassivatingScopedBean reference =
                (SerializableCustomPassivatingScopedBean) beanManager.getReference(
                        bean,
                        SerializableCustomPassivatingScopedBean.class,
                        creationalContext
                );
        reference.ping();

        assertNotNull(RecordingPassivatingContext.lastContextual);
        assertTrue(RecordingPassivatingContext.lastContextual instanceof PassivationCapable);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped managed bean with a non-passivation-capable injection point is a deployment problem")
    void shouldFailDeploymentForManagedBeanWithNonPassivationCapableInjectionPointInPassivatingScope() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonPassivationCapableDependency.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - NonPassivatingInjectedFieldTest: passivating scoped bean with non-passivation-capable field dependency is a deployment problem")
    void shouldMatchTckNonPassivatingInjectedFieldDeploymentFailure() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonPassivationCapableDependency.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - NonPassivatingConstructorParamTest: passivating scoped bean with non-passivation-capable constructor dependency is a deployment problem")
    void shouldMatchTckNonPassivatingConstructorParamDeploymentFailure() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonPassivationCapableConstructorDependency.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - NonPassivatingInitParamTest: passivating scoped bean with non-passivation-capable initializer dependency is a deployment problem")
    void shouldMatchTckNonPassivatingInitParamDeploymentFailure() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonPassivationCapableInitializerDependency.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - PassivationCapableDependencyErrorTest: passivating scope validates passivation-capable dependencies at deployment")
    void shouldMatchTckPassivationCapableDependencyErrorDeploymentFailure() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonPassivationCapableDependency.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped producer method with final non-serializable return type is a deployment problem")
    void shouldFailDeploymentForPassivatingProducerMethodWithFinalNonSerializableReturnType() {
        Syringe syringe = newSyringe(InvalidPassivatingProducerMethodHolder.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped producer field with final non-serializable type is a deployment problem")
    void shouldFailDeploymentForPassivatingProducerFieldWithFinalNonSerializableType() {
        Syringe syringe = newSyringe(InvalidPassivatingProducerFieldHolder.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped producer method that returns non-serializable values at runtime throws IllegalProductException")
    void shouldThrowIllegalProductExceptionForRuntimeNonSerializablePassivatingProducerMethodProduct() {
        Syringe syringe = newSyringe(RuntimeInvalidPassivatingProducerMethodHolder.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(PayloadContract.class));
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        assertThrows(IllegalProductException.class, () -> ((Bean) bean).create((CreationalContext) creationalContext));
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped producer field that contains non-serializable values at runtime throws IllegalProductException")
    void shouldThrowIllegalProductExceptionForRuntimeNonSerializablePassivatingProducerFieldProduct() {
        Syringe syringe = newSyringe(RuntimeInvalidPassivatingProducerFieldHolder.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(PayloadContract.class));
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        assertThrows(IllegalProductException.class, () -> ((Bean) bean).create((CreationalContext) creationalContext));
    }

    @Test
    @DisplayName("17.5.5 - A @Dependent producer returning an unserializable object for a passivation-capable dependency throws IllegalProductException")
    void shouldThrowIllegalProductExceptionForDependentProducerReturningUnserializableObjectForPassivatingInjectionPoint() {
        Syringe syringe = newSyringe(
                DependentRuntimeInvalidProducerHolder.class,
                SessionBeanWithDependentRuntimeInvalidProduct.class
        );
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("17-5-5-dependent-runtime");
        try {
            RuntimeException runtimeException = assertThrows(RuntimeException.class,
                    () -> getSessionBean(beanManager, SessionBeanWithDependentRuntimeInvalidProduct.class));
            assertTrue(containsCause(runtimeException, IllegalProductException.class));
        } finally {
            beanManager.getContextManager().deactivateSession();
        }
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped bean with a non-passivation-capable interceptor is a deployment problem")
    void shouldFailDeploymentForPassivatingBeanWithNonPassivationCapableInterceptor() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonSerializableInterceptor.class,
                NonSerializableInterceptor.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped bean with interceptor injection point that is not passivation capable is a deployment problem")
    void shouldFailDeploymentForPassivatingBeanWithInterceptorHavingNonPassivationCapableInjectionPoint() {
        Syringe syringe = newSyringe(
                SessionBeanWithInterceptorHavingNonPassivationCapableInjectionPoint.class,
                SerializableInterceptorWithNonSerializableInjectionPoint.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped bean with a non-passivation-capable decorator is a deployment problem")
    void shouldFailDeploymentForPassivatingBeanWithNonPassivationCapableDecorator() {
        Syringe syringe = newSyringe(
                SessionBeanWithNonSerializableDecorator.class,
                NonSerializableDecorator.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - A passivating scoped bean with decorator injection point that is not passivation capable is a deployment problem")
    void shouldFailDeploymentForPassivatingBeanWithDecoratorHavingNonPassivationCapableInjectionPoint() {
        Syringe syringe = newSyringe(
                SessionBeanWithDecoratorHavingNonPassivationCapableInjectionPoint.class,
                SerializableDecoratorWithNonSerializableInjectionPoint.class,
                NonSerializableDependentPlain.class
        );
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("17.5.5 - For custom Bean implementations the container uses getInjectionPoints() and InjectionPoint.isTransient()")
    void shouldUseCustomBeanInjectionPointsAndTransientMetadataForPassivationValidation() {
        CustomSessionScopedSyntheticBean transientBean =
                new CustomSessionScopedSyntheticBean(true, NonSerializableDependentPlain.class);
        Syringe valid = newSyringe(PassivationAnchor.class, NonSerializableDependentPlain.class);
        valid.getKnowledgeBase().addBean(transientBean);
        assertDoesNotThrow(valid::setup);

        CustomSessionScopedSyntheticBean nonTransientBean =
                new CustomSessionScopedSyntheticBean(false, NonSerializableDependentPlain.class);
        Syringe invalid = newSyringe(PassivationAnchor.class, NonSerializableDependentPlain.class);
        invalid.getKnowledgeBase().addBean(nonTransientBean);
        assertThrows(DeploymentException.class, invalid::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        // Exclude parity fixtures in sibling packages that otherwise leak into this deployment
        // via package scanning in the Syringe(Class<?>...) constructor.
        excludeIfPresent(syringe,
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$InvalidPassivatingProducerMethodHolder",
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$CowProducer",
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$FieldInjectionCorralBroken",
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$SetterInjectionCorralBroken",
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$ConstructorInjectionCorralBroken",
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$Corral",
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par175passivation.tckparity.PassivatingProducerTckParityTest$Cow"
        );
        return syringe;
    }

    private void excludeIfPresent(Syringe syringe, String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                syringe.exclude(clazz);
            } catch (ClassNotFoundException ignored) {
                // Optional exclusion: class may not exist in every branch/test set.
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T getSessionBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
        return (T) beanManager.getContext(SessionScoped.class).get((Contextual) bean, creationalContext);
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> expectedCauseType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedCauseType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @ApplicationScoped
    public static class PassivationAnchor {
    }

    @SessionScoped
    public static class SerializableSessionBean implements Serializable {
        private final String id = UUID.randomUUID().toString();

        String id() {
            return id;
        }
    }

    @SessionScoped
    public static class NonSerializableSessionBean {
    }

    public interface PayloadContract {
        String kind();
    }

    public static class SerializablePayload implements PayloadContract, Serializable {
        @Override
        public String kind() {
            return "serializable";
        }
    }

    public static class NonSerializablePayload implements PayloadContract {
        @Override
        public String kind() {
            return "non-serializable";
        }
    }

    @ApplicationScoped
    public static class ProducerMethodSwitch implements Serializable {
        static volatile boolean useSerializableValue = true;

        @Produces
        PayloadContract produce() {
            return useSerializableValue ? new SerializablePayload() : new NonSerializablePayload();
        }
    }

    @ApplicationScoped
    public static class ProducerFieldSwitch implements Serializable {
        static volatile PayloadContract current = new SerializablePayload();

        @Produces
        PayloadContract produced = current;
    }

    @SessionScoped
    public static class SessionBeanWithMethodProducedDependency implements Serializable {
        @Inject
        PayloadContract payload;

        String producedKind() {
            return payload.kind();
        }
    }

    @SessionScoped
    public static class SessionBeanWithFieldProducedDependency implements Serializable {
        @Inject
        PayloadContract payload;

        String producedKind() {
            return payload.kind();
        }
    }

    @ApplicationScoped
    public static class SerializableMethodProducer implements Serializable {
        @Produces
        SerializablePayload produceSerializable() {
            return new SerializablePayload();
        }
    }

    @ApplicationScoped
    public static class NonSerializableMethodProducer implements Serializable {
        @Produces
        NonSerializablePayload produceNonSerializable() {
            return new NonSerializablePayload();
        }
    }

    @SessionScoped
    public static class SessionBeanWithSerializableMethodProduct implements Serializable {
        @Inject
        SerializablePayload payload;
    }

    @SessionScoped
    public static class SessionBeanWithNonSerializableMethodProduct implements Serializable {
        @Inject
        NonSerializablePayload payload;
    }

    @ApplicationScoped
    public static class SerializableFieldProducer implements Serializable {
        @Produces
        SerializablePayload produced = new SerializablePayload();
    }

    @ApplicationScoped
    public static class NonSerializableFieldProducer implements Serializable {
        @Produces
        NonSerializablePayload produced = new NonSerializablePayload();
    }

    @SessionScoped
    public static class SessionBeanWithSerializableFieldProduct implements Serializable {
        @Inject
        SerializablePayload payload;
    }

    @SessionScoped
    public static class SessionBeanWithNonSerializableFieldProduct implements Serializable {
        @Inject
        NonSerializablePayload payload;
    }

    @Dependent
    public static class NonSerializableDependentPlain {
    }

    @Dependent
    public static class SerializableDependent implements Serializable {
    }

    @ApplicationScoped
    public static class NonSerializableNormalService {
    }

    @SessionScoped
    public static class SessionBeanWithTransientFieldDependency implements Serializable {
        @Inject
        transient NonSerializableDependentPlain dependency;
    }

    @SessionScoped
    public static class SessionBeanWithPassivationCapableFieldDependency implements Serializable {
        @Inject
        NonSerializableNormalService dependency;
    }

    @SessionScoped
    public static class SessionBeanWithTransientReferenceConstructorDependency implements Serializable {
        private final transient NonSerializableDependentPlain dependency;

        @Inject
        public SessionBeanWithTransientReferenceConstructorDependency(
                @TransientReference NonSerializableDependentPlain dependency) {
            this.dependency = dependency;
        }
    }

    @SessionScoped
    public static class SessionBeanWithPassivationCapableConstructorDependency implements Serializable {
        private final SerializableDependent dependency;

        @Inject
        public SessionBeanWithPassivationCapableConstructorDependency(SerializableDependent dependency) {
            this.dependency = dependency;
        }
    }

    @SessionScoped
    public static class SessionBeanWithTransientReferenceMethodDependency implements Serializable {
        private transient NonSerializableDependentPlain dependency;

        @Inject
        void init(@TransientReference NonSerializableDependentPlain dependency) {
            this.dependency = dependency;
        }
    }

    @SessionScoped
    public static class SessionBeanWithPassivationCapableMethodDependency implements Serializable {
        private SerializableDependent dependency;

        @Inject
        void init(SerializableDependent dependency) {
            this.dependency = dependency;
        }
    }

    @SessionScoped
    public static class SessionBeanWithNormalScopedDependency implements Serializable {
        @Inject
        NonSerializableNormalService dependency;
    }

    @SessionScoped
    public static class SessionBeanWithDependentPassivationCapableDependency implements Serializable {
        @Inject
        SerializableDependent dependency;
    }

    @SessionScoped
    public static class SessionBeanWithInstanceBuiltInDependency implements Serializable {
        @Inject
        Instance<NonSerializableNormalService> dependency;
    }

    @SessionScoped
    public static class SessionBeanWithEventBuiltInDependency implements Serializable {
        @Inject
        Event<String> dependency;
    }

    @SessionScoped
    public static class SessionBeanWithDependentInjectionPointBuiltInDependency implements Serializable {
        @Inject
        DependentWithInjectionPointMetadata dependency;
    }

    @Dependent
    public static class DependentWithInjectionPointMetadata implements Serializable {
        @Inject
        InjectionPoint dependency;
    }

    @SessionScoped
    public static class SessionBeanWithBeanManagerBuiltInDependency implements Serializable {
        @Inject
        BeanManager dependency;
    }

    @SessionScoped
    public static class SessionBeanWithCustomPassivationCapableDependency implements Serializable {
        @Inject
        @CustomPassivationDependency
        CustomPassivationCapableDependencyService dependency;
    }

    @NormalScope(passivating = true)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface CustomPassivatingScope {
    }

    public static class RecordingPassivatingContext implements Context {
        static volatile Contextual<?> lastContextual;

        private final Map<Contextual<?>, Object> instances = new ConcurrentHashMap<Contextual<?>, Object>();

        static void reset() {
            lastContextual = null;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return CustomPassivatingScope.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            lastContextual = contextual;
            Object existing = instances.get(contextual);
            if (existing != null) {
                return (T) existing;
            }
            if (creationalContext == null) {
                return null;
            }
            T created = contextual.create(creationalContext);
            instances.put(contextual, created);
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            lastContextual = contextual;
            return (T) instances.get(contextual);
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    @CustomPassivatingScope
    public static class SerializableCustomPassivatingScopedBean implements Serializable {
        public String ping() {
            return "ok";
        }
    }

    @CustomPassivatingScope
    public static class NonSerializableCustomPassivatingScopedBean {
    }

    @SessionScoped
    public static class SessionBeanWithNonPassivationCapableDependency implements Serializable {
        @Inject
        NonSerializableDependentPlain dependency;
    }

    @SessionScoped
    public static class SessionBeanWithNonPassivationCapableConstructorDependency implements Serializable {
        private final NonSerializableDependentPlain dependency;

        @Inject
        public SessionBeanWithNonPassivationCapableConstructorDependency(NonSerializableDependentPlain dependency) {
            this.dependency = dependency;
        }
    }

    @SessionScoped
    public static class SessionBeanWithNonPassivationCapableInitializerDependency implements Serializable {
        private NonSerializableDependentPlain dependency;

        @Inject
        void init(NonSerializableDependentPlain dependency) {
            this.dependency = dependency;
        }
    }

    public static final class FinalNonSerializableValue {
        String value() {
            return "x";
        }
    }

    @ApplicationScoped
    public static class InvalidPassivatingProducerMethodHolder {
        @Produces
        @SessionScoped
        FinalNonSerializableValue produceInvalid() {
            return new FinalNonSerializableValue();
        }
    }

    @ApplicationScoped
    public static class InvalidPassivatingProducerFieldHolder {
        @Produces
        @SessionScoped
        FinalNonSerializableValue produced = new FinalNonSerializableValue();
    }

    @ApplicationScoped
    public static class RuntimeInvalidPassivatingProducerMethodHolder implements Serializable {
        @Produces
        @SessionScoped
        PayloadContract produceInvalidRuntime() {
            return new NonSerializablePayload();
        }
    }

    @ApplicationScoped
    public static class RuntimeInvalidPassivatingProducerFieldHolder implements Serializable {
        @Produces
        @SessionScoped
        PayloadContract produced = new NonSerializablePayload();
    }

    @SessionScoped
    public static class SessionBeanWithRuntimeInvalidMethodProduct implements Serializable {
        @Inject
        PayloadContract payload;
    }

    @SessionScoped
    public static class SessionBeanWithRuntimeInvalidFieldProduct implements Serializable {
        @Inject
        PayloadContract payload;
    }

    @ApplicationScoped
    public static class DependentRuntimeInvalidProducerHolder implements Serializable {
        @Produces
        @Dependent
        PayloadContract produceDependentInvalidRuntime() {
            return new NonSerializablePayload();
        }
    }

    @SessionScoped
    public static class SessionBeanWithDependentRuntimeInvalidProduct implements Serializable {
        @Inject
        PayloadContract payload;
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface PassivationBinding {
    }

    @SessionScoped
    @PassivationBinding
    public static class SessionBeanWithNonSerializableInterceptor implements Serializable {
        String ping() {
            return "ok";
        }
    }

    @Interceptor
    @PassivationBinding
    @Priority(1)
    public static class NonSerializableInterceptor {
        @AroundInvoke
        public Object intercept(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    @SessionScoped
    @PassivationBinding
    public static class SessionBeanWithInterceptorHavingNonPassivationCapableInjectionPoint implements Serializable {
        String ping() {
            return "ok";
        }
    }

    @Interceptor
    @PassivationBinding
    @Priority(2)
    public static class SerializableInterceptorWithNonSerializableInjectionPoint implements Serializable {
        @Inject
        NonSerializableDependentPlain dependency;

        @AroundInvoke
        public Object intercept(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    interface DecoratedService {
        String ping();
    }

    @SessionScoped
    public static class SessionBeanWithNonSerializableDecorator implements DecoratedService, Serializable {
        @Override
        public String ping() {
            return "bean";
        }
    }

    @Decorator
    @Priority(10)
    public static class NonSerializableDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Override
        public String ping() {
            return "decorated:" + delegate.ping();
        }
    }

    @SessionScoped
    public static class SessionBeanWithDecoratorHavingNonPassivationCapableInjectionPoint
            implements DecoratedService, Serializable {
        @Override
        public String ping() {
            return "bean";
        }
    }

    @Decorator
    @Priority(20)
    public static class SerializableDecoratorWithNonSerializableInjectionPoint
            implements DecoratedService, Serializable {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Inject
        NonSerializableDependentPlain dependency;

        @Override
        public String ping() {
            return "decorated:" + delegate.ping();
        }
    }

    static class CustomPassivationCapableBean implements Bean<CustomPassivationCapableService>, PassivationCapable {
        private static final Set<Type> TYPES;
        private static final Set<Annotation> QUALIFIERS;

        static {
            Set<Type> types = new HashSet<Type>();
            types.add(CustomPassivationCapableService.class);
            types.add(Object.class);
            TYPES = Collections.unmodifiableSet(types);

            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            QUALIFIERS = Collections.unmodifiableSet(qualifiers);
        }

        private final String id = getClass().getPackage().getName() + ".CustomPassivationCapableBean#" +
                UUID.randomUUID().toString();

        @Override
        public String getId() {
            return id;
        }

        @Override
        public CustomPassivationCapableService create(CreationalContext<CustomPassivationCapableService> creationalContext) {
            return new CustomPassivationCapableService();
        }

        @Override
        public void destroy(CustomPassivationCapableService instance,
                            CreationalContext<CustomPassivationCapableService> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public Class<?> getBeanClass() {
            return CustomPassivationCapableService.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return QUALIFIERS;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return TYPES;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    public static class CustomPassivationCapableService {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface CustomPassivationDependency {
    }

    static final class CustomPassivationDependencyLiteral
            extends AnnotationLiteral<CustomPassivationDependency>
            implements CustomPassivationDependency {
        static final CustomPassivationDependencyLiteral INSTANCE = new CustomPassivationDependencyLiteral();
    }

    static class CustomPassivationCapableDependencyBean
            implements Bean<CustomPassivationCapableDependencyService>, PassivationCapable {
        private static final Set<Type> TYPES;
        private static final Set<Annotation> QUALIFIERS;

        static {
            Set<Type> types = new HashSet<Type>();
            types.add(CustomPassivationCapableDependencyService.class);
            types.add(Object.class);
            TYPES = Collections.unmodifiableSet(types);

            Set<Annotation> qualifiers = new HashSet<Annotation>();
            qualifiers.add(Any.Literal.INSTANCE);
            qualifiers.add(CustomPassivationDependencyLiteral.INSTANCE);
            QUALIFIERS = Collections.unmodifiableSet(qualifiers);
        }

        private final String id = getClass().getPackage().getName() + ".CustomPassivationCapableDependencyBean#" +
                UUID.randomUUID().toString();

        @Override
        public String getId() {
            return id;
        }

        @Override
        public CustomPassivationCapableDependencyService create(
                CreationalContext<CustomPassivationCapableDependencyService> creationalContext) {
            return new CustomPassivationCapableDependencyService();
        }

        @Override
        public void destroy(CustomPassivationCapableDependencyService instance,
                            CreationalContext<CustomPassivationCapableDependencyService> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public Class<?> getBeanClass() {
            return CustomPassivationCapableDependencyService.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return QUALIFIERS;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return TYPES;
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    public static class CustomPassivationCapableDependencyService {
    }

    static class CustomSessionScopedSyntheticBean
            implements Bean<CustomSessionScopedSyntheticBeanService>, PassivationCapable {
        private final String id = getClass().getPackage().getName() + ".CustomSessionScopedSyntheticBean#" +
                UUID.randomUUID().toString();
        private final Set<InjectionPoint> injectionPoints;

        CustomSessionScopedSyntheticBean(boolean transientInjectionPoint, Type type) {
            this.injectionPoints = Collections.<InjectionPoint>singleton(
                    new SyntheticInjectionPoint(type, this, transientInjectionPoint));
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public CustomSessionScopedSyntheticBeanService create(
                CreationalContext<CustomSessionScopedSyntheticBeanService> creationalContext) {
            return new CustomSessionScopedSyntheticBeanService();
        }

        @Override
        public void destroy(CustomSessionScopedSyntheticBeanService instance,
                            CreationalContext<CustomSessionScopedSyntheticBeanService> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public Class<?> getBeanClass() {
            return CustomSessionScopedSyntheticBeanService.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return injectionPoints;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return new HashSet<Annotation>(Arrays.<Annotation>asList(
                    Default.Literal.INSTANCE, Any.Literal.INSTANCE));
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return SessionScoped.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return new HashSet<Type>(Arrays.<Type>asList(
                    CustomSessionScopedSyntheticBeanService.class, Object.class));
        }

        @Override
        public boolean isAlternative() {
            return false;
        }
    }

    public static class CustomSessionScopedSyntheticBeanService implements Serializable {
    }

    static class SyntheticInjectionPoint implements InjectionPoint {
        private final Type type;
        private final Bean<?> bean;
        private final boolean isTransient;

        SyntheticInjectionPoint(Type type, Bean<?> bean, boolean isTransient) {
            this.type = type;
            this.bean = bean;
            this.isTransient = isTransient;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return new HashSet<Annotation>(Arrays.<Annotation>asList(
                    Default.Literal.INSTANCE, Any.Literal.INSTANCE));
        }

        @Override
        public Bean<?> getBean() {
            return bean;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public Annotated getAnnotated() {
            return null;
        }

        @Override
        public boolean isDelegate() {
            return false;
        }

        @Override
        public boolean isTransient() {
            return isTransient;
        }
    }

    static class NonBeanPassivationCapableContextual
            implements Contextual<Object>, PassivationCapable, Serializable {
        private final String id = getClass().getPackage().getName() + ".NonBeanPassivationCapableContextual#" +
                UUID.randomUUID().toString();

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return new Object();
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }
    }
}
