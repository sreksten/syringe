package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet1.InheritedQualifierWithValue;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet1.QualifierChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet1.QualifierGrandChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet2.InheritedScopedStereotype;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet2.NonInheritedStereotype;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet2.NonInheritedStereotypeChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet2.StereotypeChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3.InheritedBindingChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3.MiddleTraceInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3.NonInheritedBindingChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3.NonInheritedTraceInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3.RootTraceInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet4.ScopedChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet4.ScopedGrandChildBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet4.ScopedIntermediateBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet4.ScopedStereotypedChildBean;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorResolver;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("4.1 - Inheritance of Type level metadata")
public class TypeLevelMetadataTest {

    @Test
    @DisplayName("4.1 - Qualifier annotated @Inherited is inherited when no intermediate class declares same qualifier")
    void qualifierAnnotatedInheritedIsInheritedWithoutIntermediateOverride() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), QualifierChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, QualifierChildBean.class);
        InheritedQualifierWithValue qualifier = findQualifier(bean, InheritedQualifierWithValue.class);

        assertEquals("parent", qualifier.value());
    }

    @Test
    @DisplayName("4.1 - Intermediate class declaring same inherited qualifier type overrides ancestor value")
    void intermediateClassQualifierOverridesAncestorQualifier() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), QualifierGrandChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, QualifierGrandChildBean.class);
        InheritedQualifierWithValue qualifier = findQualifier(bean, InheritedQualifierWithValue.class);

        assertEquals("intermediate", qualifier.value());
    }

    @Test
    @DisplayName("4.1 - Stereotype annotated @Inherited is inherited by managed bean class")
    void inheritedStereotypeIsInheritedByManagedBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StereotypeChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, StereotypeChildBean.class);

        assertTrue(bean.getStereotypes().contains(InheritedScopedStereotype.class));
        assertEquals(RequestScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("4.1 - Stereotype without @Inherited is not inherited by managed bean class")
    void nonInheritedStereotypeIsNotInheritedByManagedBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonInheritedStereotypeChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, NonInheritedStereotypeChildBean.class);

        assertFalse(bean.getStereotypes().contains(NonInheritedStereotype.class));
    }

    @Test
    @DisplayName("4.1 - Interceptor binding annotated @Inherited is inherited and nearest declaration wins")
    void inheritedInterceptorBindingIsInheritedAndNearestDeclarationWins() throws NoSuchMethodException {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InheritedBindingChildBean.class);
        syringe.setup();

        Method method = InheritedBindingChildBean.class.getMethod("ping");
        InterceptorResolver resolver = new InterceptorResolver(syringe.getKnowledgeBase());
        List<Class<?>> interceptorClasses = resolver.resolve(
                        InheritedBindingChildBean.class,
                        method,
                        InterceptionType.AROUND_INVOKE
                ).stream()
                .map(InterceptorInfo::getInterceptorClass)
                .collect(Collectors.toList());

        assertFalse(interceptorClasses.contains(RootTraceInterceptor.class));
        assertTrue(interceptorClasses.contains(MiddleTraceInterceptor.class));
    }

    @Test
    @DisplayName("4.1 - Interceptor binding without @Inherited is not inherited")
    void nonInheritedInterceptorBindingIsNotInherited() throws NoSuchMethodException {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonInheritedBindingChildBean.class);
        syringe.setup();

        Method method = NonInheritedBindingChildBean.class.getMethod("ping");
        InterceptorResolver resolver = new InterceptorResolver(syringe.getKnowledgeBase());
        List<InterceptorInfo> interceptors = resolver.resolve(
                NonInheritedBindingChildBean.class,
                method,
                InterceptionType.AROUND_INVOKE
        );

        assertTrue(interceptors.isEmpty());
    }

    @Test
    @DisplayName("4.1 - Inherited scope is applied when no intermediate class declares a scope")
    void inheritedScopeIsAppliedWhenNoIntermediateClassDeclaresScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopedChildBean.class);
        syringe.exclude(ScopedIntermediateBean.class, ScopedGrandChildBean.class, ScopedStereotypedChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, ScopedChildBean.class);
        assertEquals(ApplicationScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("4.1 - Intermediate scope declaration blocks ancestor scope inheritance")
    void intermediateScopeDeclarationBlocksAncestorScopeInheritance() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopedGrandChildBean.class);
        syringe.exclude(ScopedChildBean.class, ScopedStereotypedChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, ScopedGrandChildBean.class);
        assertEquals(RequestScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("4.1 - Inherited explicit scope takes precedence over stereotype default scope")
    void inheritedExplicitScopeTakesPrecedenceOverStereotypeDefaultScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopedStereotypedChildBean.class);
        syringe.exclude(ScopedChildBean.class, ScopedIntermediateBean.class, ScopedGrandChildBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, ScopedStereotypedChildBean.class);
        assertEquals(ApplicationScoped.class, bean.getScope());
    }

    private Bean<?> findBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(bean -> bean.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bean not found: " + beanClass.getName()));
    }

    private <A extends Annotation> A findQualifier(Bean<?> bean, Class<A> qualifierType) {
        Optional<Annotation> annotation = bean.getQualifiers().stream()
                .filter(candidate -> candidate.annotationType().equals(qualifierType))
                .findFirst();

        if (!annotation.isPresent()) {
            throw new AssertionError("Qualifier not found: " + qualifierType.getName());
        }

        return qualifierType.cast(annotation.get());
    }
}
