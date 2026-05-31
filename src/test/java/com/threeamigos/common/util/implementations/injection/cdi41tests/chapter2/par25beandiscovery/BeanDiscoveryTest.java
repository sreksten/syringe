package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet1.DiscoveryAnchorBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet1.NonBeanClass;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet2.MethodProducedObject;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet2.MethodProducerConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet3.FieldProducedObject;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet3.FieldProducerConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet4.DisposerDiscoveryAnchorBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet4.NonBeanInvalidDisposerHolder;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet5.NonBeanObserverHolder;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet5.ObserverDiscoveryAnchorBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2.5 - Default Bean Discovery Mode")
public class BeanDiscoveryTest {

    @Test
    @DisplayName("2.5 - Bean classes without bean defining annotation are not discovered")
    void beanClassesWithoutBeanDefiningAnnotationAreNotDiscovered() {
        // Given: no explicit bean-discovery override, default mode is annotated
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DiscoveryAnchorBean.class);
        syringe.setup();
        // When / Then
        assertThrows(UnsatisfiedResolutionException.class, () -> syringe.inject(NonBeanClass.class));
    }

    @Test
    @DisplayName("2.5 - Producer methods whose bean class lacks bean defining annotation are not discovered")
    void producerMethodsWhoseBeanClassLacksBeanDefiningAnnotationAreNotDiscovered() {
        // Given: consumer requires product that only exists via producer method on a non-bean class
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MethodProducerConsumer.class);
        // When / Then
        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(syringe.getKnowledgeBase().getInjectionErrors().stream()
                .anyMatch(error -> error.contains(MethodProducedObject.class.getName())));
    }

    @Test
    @DisplayName("2.5 - Producer fields whose bean class lacks bean defining annotation are not discovered")
    void producerFieldsWhoseBeanClassLacksBeanDefiningAnnotationAreNotDiscovered() {
        // Given: consumer requires product that only exists via producer field on a non-bean class
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FieldProducerConsumer.class);
        // When / Then
        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(syringe.getKnowledgeBase().getInjectionErrors().stream()
                .anyMatch(error -> error.contains(FieldProducedObject.class.getName())));
    }

    @Test
    @DisplayName("2.5 - Disposer methods whose bean class lacks bean defining annotation are not discovered")
    void disposerMethodsWhoseBeanClassLacksBeanDefiningAnnotationAreNotDiscovered() {
        // Given: class contains invalid disposer signature but no bean defining annotation
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DisposerDiscoveryAnchorBean.class);
        // When
        syringe.setup();
        // Then
        assertTrue(syringe.getKnowledgeBase().getDefinitionErrors().stream()
                .noneMatch(error -> error.contains(NonBeanInvalidDisposerHolder.class.getName())));
        assertTrue(syringe.getKnowledgeBase().getBeans().stream()
                .noneMatch(bean -> bean.getBeanClass().equals(NonBeanInvalidDisposerHolder.class)));
    }

    @Test
    @DisplayName("2.5 - Observer methods whose bean class lacks bean defining annotation are not discovered")
    void observerMethodsWhoseBeanClassLacksBeanDefiningAnnotationAreNotDiscovered() {
        // Given: class has observer method but no bean defining annotation
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ObserverDiscoveryAnchorBean.class);
        syringe.setup();
        // Then
        assertTrue(syringe.getKnowledgeBase().getBeans().stream()
                .noneMatch(bean -> bean.getBeanClass().equals(NonBeanObserverHolder.class)));
        assertTrue(syringe.getKnowledgeBase().getObserverMethodInfos().stream()
                .noneMatch(info -> info.getDeclaringBean() != null
                        && info.getDeclaringBean().getBeanClass().equals(NonBeanObserverHolder.class)));
    }

}
