package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet1.ChildWithInheritedInjectedField;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2.ChildOverridingInheritableMethods;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2.ChildWithInheritedMemberMethods;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2.InheritedMemberEvent;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet2.ParentWithInheritableMethods;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet3.ChildOverridingInterceptedMethod;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet3.ChildWithInheritedInterceptedMethod;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet3.InheritedMethodBindingInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet4.ChildWithoutInheritedProducerAndDisposerMethods;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet4.ParentWithProducerAndDisposerMethods;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet5.ChildWithoutInheritedProducerField;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet5.ParentWithProducerField;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6.GenericObserverParent;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6.StringGenericInjectionBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6.StringGenericObserverBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6.StringGenericService;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorResolver;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InterceptionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("4.2 - Inheritance of Member level metadata")
public class MemberLevelMetadataTest {

    @Test
    @DisplayName("4.2 - Injected field declared by superclass is inherited by bean class")
    void injectedFieldDeclaredBySuperclassIsInherited() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildWithInheritedInjectedField.class);
        syringe.setup();

        ChildWithInheritedInjectedField instance = createManagedBeanInstance(syringe, ChildWithInheritedInjectedField.class);
        assertNotNull(instance.getInheritedDependency());
    }

    @Test
    @DisplayName("4.2 - Initializer, non-static observer, @PostConstruct and @PreDestroy are inherited when not overridden")
    void inheritableMethodsAreInvokedWhenNotOverridden() {
        ChildWithInheritedMemberMethods.resetState();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildWithInheritedMemberMethods.class);
        syringe.exclude(ChildOverridingInheritableMethods.class);
        syringe.setup();

        BeanImpl<ChildWithInheritedMemberMethods> bean = findManagedBean(syringe, ChildWithInheritedMemberMethods.class);
        CreationalContext<ChildWithInheritedMemberMethods> creationalContext = syringe.getBeanManager()
                .createCreationalContext(bean);
        ChildWithInheritedMemberMethods instance = bean.create(creationalContext);

        assertNotNull(instance.getInitializerDependency());
        assertEquals(1, ParentWithInheritableMethods.initializerCalls);
        assertEquals(1, ParentWithInheritableMethods.postConstructCalls);

        syringe.getBeanManager().getEvent().select(InheritedMemberEvent.class).fire(new InheritedMemberEvent());
        assertEquals(1, ParentWithInheritableMethods.observerCalls);

        bean.destroy(instance, creationalContext);
        assertEquals(1, ParentWithInheritableMethods.preDestroyCalls);
    }

    @Test
    @DisplayName("4.2 - Overriding initializer, observer, @PostConstruct and @PreDestroy prevents inheritance")
    void overriddenMethodsAreNotInherited() {
        ChildOverridingInheritableMethods.resetState();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildOverridingInheritableMethods.class);
        syringe.exclude(ChildWithInheritedMemberMethods.class);
        syringe.setup();

        BeanImpl<ChildOverridingInheritableMethods> bean = findManagedBean(syringe, ChildOverridingInheritableMethods.class);
        CreationalContext<ChildOverridingInheritableMethods> creationalContext = syringe.getBeanManager()
                .createCreationalContext(bean);
        ChildOverridingInheritableMethods instance = bean.create(creationalContext);

        assertEquals(0, ParentWithInheritableMethods.initializerCalls);
        assertEquals(0, ChildOverridingInheritableMethods.overridingInitializerCalls);
        assertEquals(0, ParentWithInheritableMethods.postConstructCalls);
        assertEquals(0, ChildOverridingInheritableMethods.overridingPostConstructCalls);

        syringe.getBeanManager().getEvent().select(InheritedMemberEvent.class).fire(new InheritedMemberEvent());
        assertEquals(0, ParentWithInheritableMethods.observerCalls);
        assertEquals(0, ChildOverridingInheritableMethods.overridingObserverCalls);

        bean.destroy(instance, creationalContext);
        assertEquals(0, ParentWithInheritableMethods.preDestroyCalls);
        assertEquals(0, ChildOverridingInheritableMethods.overridingPreDestroyCalls);
    }

    @Test
    @DisplayName("4.2 - Method-level interceptor binding on non-static superclass method is inherited when method is not overridden")
    void methodLevelInterceptorBindingIsInheritedWhenMethodIsNotOverridden() throws NoSuchMethodException {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildWithInheritedInterceptedMethod.class);
        syringe.exclude(ChildOverridingInterceptedMethod.class);
        syringe.setup();

        Method method = ChildWithInheritedInterceptedMethod.class.getMethod("ping");
        InterceptorResolver resolver = new InterceptorResolver(syringe.getKnowledgeBase());
        List<Class<?>> interceptorClasses = resolver.resolve(
                        ChildWithInheritedInterceptedMethod.class,
                        method,
                        InterceptionType.AROUND_INVOKE
                ).stream()
                .map(InterceptorInfo::getInterceptorClass)
                .collect(Collectors.toList());

        assertEquals(1, interceptorClasses.size());
        assertEquals(InheritedMethodBindingInterceptor.class, interceptorClasses.get(0));
    }

    @Test
    @DisplayName("4.2 - Method-level interceptor binding is not inherited when method is overridden")
    void methodLevelInterceptorBindingIsNotInheritedWhenMethodIsOverridden() throws NoSuchMethodException {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildOverridingInterceptedMethod.class);
        syringe.exclude(ChildWithInheritedInterceptedMethod.class);
        syringe.setup();

        Method method = ChildOverridingInterceptedMethod.class.getMethod("ping");
        InterceptorResolver resolver = new InterceptorResolver(syringe.getKnowledgeBase());
        List<InterceptorInfo> interceptors = resolver.resolve(
                ChildOverridingInterceptedMethod.class,
                method,
                InterceptionType.AROUND_INVOKE
        );

        assertEquals(0, interceptors.size());
    }

    @Test
    @DisplayName("4.2 - Non-static producer and disposer methods declared by superclass are not inherited by subclass bean")
    void nonStaticProducerAndDisposerMethodsAreNotInheritedBySubclassBean() {
        ParentWithProducerAndDisposerMethods.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildWithoutInheritedProducerAndDisposerMethods.class);
        syringe.setup();

        List<ProducerBean<?>> parentMethodProducers = findMethodProducers(syringe, ParentWithProducerAndDisposerMethods.class);
        List<ProducerBean<?>> childMethodProducers = findMethodProducers(syringe, ChildWithoutInheritedProducerAndDisposerMethods.class);

        assertEquals(1, parentMethodProducers.size());
        assertNotNull(parentMethodProducers.get(0).getDisposerMethod());
        assertEquals(0, childMethodProducers.size());
    }

    @Test
    @DisplayName("4.2 - Non-static producer field declared by superclass is not inherited by subclass bean")
    void nonStaticProducerFieldIsNotInheritedBySubclassBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildWithoutInheritedProducerField.class);
        syringe.setup();

        List<ProducerBean<?>> parentFieldProducers = findFieldProducers(syringe, ParentWithProducerField.class);
        List<ProducerBean<?>> childFieldProducers = findFieldProducers(syringe, ChildWithoutInheritedProducerField.class);

        assertEquals(1, parentFieldProducers.size());
        assertEquals(0, childFieldProducers.size());
    }

    @Test
    @DisplayName("4.2 - Inherited injection point type variables are substituted with subclass type arguments")
    void inheritedInjectionPointTypeVariablesAreSubstitutedWithSubclassTypeArguments() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StringGenericInjectionBean.class);
        syringe.setup();

        StringGenericInjectionBean instance = createManagedBeanInstance(syringe, StringGenericInjectionBean.class);

        assertNotNull(instance.getService());
        assertEquals(StringGenericService.class, instance.getService().getClass());
        assertEquals(String.class, instance.getService().payloadType());
    }

    @Test
    @DisplayName("4.2 - Inherited observer parameter type variables are substituted with subclass type arguments")
    void inheritedObserverParameterTypeVariablesAreSubstitutedWithSubclassTypeArguments() {
        GenericObserverParent.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StringGenericObserverBean.class);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(String.class).fire("generic-event");

        assertEquals(1, GenericObserverParent.observedCount);
        assertEquals("generic-event", GenericObserverParent.lastObserved);
    }

    @SuppressWarnings("unchecked")
    private <T> T createManagedBeanInstance(Syringe syringe, Class<T> beanClass) {
        BeanImpl<T> bean = findManagedBean(syringe, beanClass);
        CreationalContext<T> creationalContext = syringe.getBeanManager().createCreationalContext(bean);
        return bean.create(creationalContext);
    }

    @SuppressWarnings("unchecked")
    private <T> BeanImpl<T> findManagedBean(Syringe syringe, Class<T> beanClass) {
        return (BeanImpl<T>) syringe.getKnowledgeBase().getBeans().stream()
                .filter(BeanImpl.class::isInstance)
                .map(BeanImpl.class::cast)
                .filter(candidate -> candidate.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Managed bean not found: " + beanClass.getName()));
    }

    private List<ProducerBean<?>> findMethodProducers(Syringe syringe, Class<?> declaringClass) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .filter(producerBean -> producerBean.getProducerMethod() != null)
                .collect(Collectors.toList());
    }

    private List<ProducerBean<?>> findFieldProducers(Syringe syringe, Class<?> declaringClass) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .filter(producerBean -> producerBean.getProducerField() != null)
                .collect(Collectors.toList());
    }
}
