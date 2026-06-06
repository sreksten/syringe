package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet1.InheritedMetadataConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet1.InheritedQualifiedChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet1.InheritedQualifier;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet2.InterfaceMetadataConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet3.ChildWithInheritedMembers;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet4.ChildObserverBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet4.InheritedObserverEvent;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet4.ParentObserverBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet5.ReusedBaseBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet5.ReusingBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet6.SpecializationConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet6.SpecializingBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("4 - Inheritance")
public class InheritanceTests {

    @Test
    @DisplayName("4 - Type-level metadata annotated @Inherited is inherited from superclass")
    void inheritedTypeLevelMetadataIsInheritedFromSuperclass() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InheritedMetadataConsumer.class);
        syringe.setup();

        InheritedMetadataConsumer consumer = createManagedBeanInstance(syringe, InheritedMetadataConsumer.class);
        assertNotNull(consumer.getBean());

        Bean<?> childBean = findManagedBean(syringe, InheritedQualifiedChildBean.class);
        assertTrue(childBean.getQualifiers().stream()
                .anyMatch(qualifier -> qualifier.annotationType().equals(InheritedQualifier.class)));
    }

    @Test
    @DisplayName("4 - Type-level metadata declared on interfaces is not inherited by implementing beans")
    void interfaceTypeLevelMetadataIsNotInherited() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InterfaceMetadataConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("4 - Injected fields, initializer methods and lifecycle callbacks are inherited from superclass")
    void injectedMembersAndLifecycleCallbacksAreInheritedFromSuperclass() {
        ChildWithInheritedMembers.resetState();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildWithInheritedMembers.class);
        syringe.setup();

        ChildWithInheritedMembers child = createManagedBeanInstance(syringe, ChildWithInheritedMembers.class);

        assertNotNull(child.getInjectedField());
        assertNotNull(child.getInitializerDependency());
        assertTrue(ChildWithInheritedMembers.isInitializerInvoked());
        assertTrue(ChildWithInheritedMembers.isPostConstructInvoked());
    }

    @Test
    @DisplayName("4 - Non-static observer methods are inherited from superclass")
    void nonStaticObserverMethodsAreInheritedFromSuperclass() {
        ParentObserverBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChildObserverBean.class);
        syringe.setup();

        syringe.getBeanManager()
                .getEvent()
                .select(InheritedObserverEvent.class)
                .fire(new InheritedObserverEvent());

        assertEquals(1, ParentObserverBean.observedEvents);
    }

    @Test
    @DisplayName("4 - Java implementation reuse is default and both base and subclass beans are available")
    void javaImplementationReuseIsDefault() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ReusingBean.class);
        syringe.setup();

        ReusedBaseBean baseBean = createManagedBeanInstance(syringe, ReusedBaseBean.class);
        ReusingBean reusingBean = createManagedBeanInstance(syringe, ReusingBean.class);

        assertEquals("base", baseBean.role());
        assertEquals("reused", reusingBean.role());
    }

    @Test
    @DisplayName("4 - A specializing bean replaces the specialized bean for the same role")
    void specializingBeanReplacesSpecializedBeanForSameRole() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SpecializationConsumer.class);
        syringe.setup();

        SpecializationConsumer consumer = createManagedBeanInstance(syringe, SpecializationConsumer.class);

        assertNotNull(consumer.getBean());
        assertEquals(SpecializingBean.class, consumer.getBean().getClass());
        assertEquals("specialized", consumer.getBean().role());
    }

    @SuppressWarnings("unchecked")
    private <T> T createManagedBeanInstance(Syringe syringe, Class<T> beanClass) {
        Bean<?> bean = findManagedBean(syringe, beanClass);
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(bean);
        return (T) bean.create((CreationalContext) creationalContext);
    }

    private Bean<?> findManagedBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(candidate -> candidate.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Managed bean not found: " + beanClass.getName()));
    }

}
