package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par38defaultqualifier;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par38defaultqualifier.bullet1.UnqualifiedInjectionPointBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.8 - Default qualifier at injection points")
public class DefaultQualifierAtInjectionPointsTest {

    @Test
    @DisplayName("3.8 - Injection point with no declared qualifier has exactly @Default")
    void unqualifiedInjectionPointHasExactlyDefaultQualifier() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), UnqualifiedInjectionPointBean.class);
        syringe.setup();

        Bean<?> bean = findManagedBean(syringe, UnqualifiedInjectionPointBean.class);
        InjectionPoint fieldInjectionPoint = bean.getInjectionPoints().stream()
                .filter(candidate -> candidate.getMember() instanceof Field)
                .filter(candidate -> candidate.getMember().getName().equals("dependency"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Injection point not found: dependency"));

        assertEquals(1, fieldInjectionPoint.getQualifiers().size());
        Annotation qualifier = fieldInjectionPoint.getQualifiers().iterator().next();
        assertTrue(qualifier.annotationType().equals(Default.class));
    }

    private Bean<?> findManagedBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(candidate -> candidate.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Managed bean not found: " + beanClass.getName()));
    }
}
