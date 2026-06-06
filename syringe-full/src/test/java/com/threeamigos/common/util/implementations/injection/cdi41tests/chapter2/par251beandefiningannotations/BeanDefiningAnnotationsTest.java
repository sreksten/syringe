package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet1.ApplicationScopedImplicitBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet1.RequestScopedImplicitBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet2.CustomNormalScopedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet3.LoggedInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet4.StereotypedServiceBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet5.DependentScopedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet6.PseudoScopedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet7.StereotypedPseudoScopedBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2.5.1 - Bean defining annotations")
public class BeanDefiningAnnotationsTest {

    @Test
    @DisplayName("2.5.1 - @ApplicationScoped and @RequestScoped are bean defining annotations")
    void applicationScopedAndRequestScopedAreBeanDefiningAnnotations() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ApplicationScopedImplicitBean.class);
        syringe.setup();

        assertTrue(hasBean(syringe, ApplicationScopedImplicitBean.class));
        assertTrue(hasBean(syringe, RequestScopedImplicitBean.class));
    }

    @Test
    @DisplayName("2.5.1 - Other normal scope types are bean defining annotations")
    void otherNormalScopeTypesAreBeanDefiningAnnotations() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), CustomNormalScopedBean.class);
        syringe.setup();

        assertTrue(hasBean(syringe, CustomNormalScopedBean.class));
    }

    @Test
    @DisplayName("2.5.1 - @Interceptor is a bean defining annotation")
    void interceptorIsBeanDefiningAnnotation() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LoggedInterceptor.class);
        syringe.setup();

        assertTrue(syringe.getKnowledgeBase().getInterceptors().contains(LoggedInterceptor.class));
        assertTrue(syringe.getKnowledgeBase().getInterceptorInfos().stream()
                .anyMatch(info -> info.getInterceptorClass().equals(LoggedInterceptor.class)));
    }

    @Test
    @DisplayName("2.5.1 - Stereotype annotations are bean defining annotations")
    void stereotypesAreBeanDefiningAnnotations() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StereotypedServiceBean.class);
        syringe.setup();

        assertTrue(hasBean(syringe, StereotypedServiceBean.class));
    }

    @Test
    @DisplayName("2.5.1 - @Dependent is a bean defining annotation")
    void dependentIsBeanDefiningAnnotation() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DependentScopedBean.class);
        syringe.setup();

        assertTrue(hasBean(syringe, DependentScopedBean.class));
    }

    @Test
    @DisplayName("2.5.1 note - Pseudo-scopes except @Dependent are not bean defining annotations")
    void pseudoScopesExceptDependentAreNotBeanDefiningAnnotations() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PseudoScopedBean.class);
        syringe.setup();

        assertFalse(hasBean(syringe, PseudoScopedBean.class));
    }

    @Test
    @DisplayName("2.5.1 note - A stereotype including a pseudo-scope annotation is bean defining")
    void stereotypeIncludingPseudoScopeIsBeanDefiningAnnotation() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StereotypedPseudoScopedBean.class);
        syringe.setup();

        assertTrue(hasBean(syringe, StereotypedPseudoScopedBean.class));
    }

    private boolean hasBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .anyMatch(bean -> bean.getBeanClass().equals(beanClass));
    }
}
