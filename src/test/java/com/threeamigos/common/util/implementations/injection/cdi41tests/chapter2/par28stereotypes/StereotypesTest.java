package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet1.StereotypedRequestBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet2.InvalidStereotypedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet3.DefaultActionBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet3.ExplicitDependentActionBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet4.ActionBoundBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet4.SecureInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet4.TransactionalInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet5.NamedActionBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet6.InvalidNamedActionBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet7.InvalidQualifiedStereotypeBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet8.InvalidTypedStereotypeBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet9.MockStrategy;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet9.StrategyClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.conflict.ConflictingPriorityBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.enabling.EnabledServiceClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.explicit.ExplicitPriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.ordering.OrderedServiceClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet11.invalidtarget.InvalidTargetStereotypeBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet11.transitive.DeepAuditableActionBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.locations.LocationLoginAction;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.locations.LocationMethodProduct;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.locations.LocationFieldProduct;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.multiple.DaoStereotype;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.multiple.MultiAction;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.multiple.MultiStereotypeLoginAction;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet12.override.OverrideMockLoginAction;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorResolver;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Paragraph 2.8 - Stereotypes")
public class StereotypesTest {

    @Test
    @DisplayName("2.8 - Stereotype default scope is inherited by the bean")
    void stereotypeDefaultScopeIsInheritedByBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StereotypedRequestBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, StereotypedRequestBean.class);
        assertEquals(RequestScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("2.8.1.1 - Stereotype declaring more than one scope is a definition error")
    void stereotypeDeclaringMoreThanOneScopeIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidStereotypedBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8.1.1 - Action stereotype defaults bean scope to @RequestScoped")
    void actionStereotypeDefaultsBeanScopeToRequestScoped() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DefaultActionBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, DefaultActionBean.class);
        assertEquals(RequestScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("2.8.1.1 - Explicit bean scope overrides Action stereotype default scope")
    void explicitBeanScopeOverridesActionStereotypeDefaultScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ExplicitDependentActionBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, ExplicitDependentActionBean.class);
        assertEquals(Dependent.class, bean.getScope());
    }

    @Test
    @DisplayName("2.8.1.2 - Interceptor bindings declared by stereotype apply to the bean")
    void interceptorBindingsDeclaredByStereotypeApplyToBean() throws NoSuchMethodException {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ActionBoundBean.class);
        syringe.setup();

        Method executeMethod = ActionBoundBean.class.getMethod("execute");
        InterceptorResolver resolver = new InterceptorResolver(syringe.getKnowledgeBase());
        List<InterceptorInfo> interceptors = resolver.resolve(
                ActionBoundBean.class,
                executeMethod,
                InterceptionType.AROUND_INVOKE
        );

        assertIterableEquals(
                Arrays.asList(SecureInterceptor.class, TransactionalInterceptor.class),
                interceptors.stream()
                        .map(InterceptorInfo::getInterceptorClass)
                        .collect(Collectors.toList())
        );
    }

    @Test
    @DisplayName("2.8.1.3 - Empty @Named on stereotype gives bean a defaulted name")
    void emptyNamedOnStereotypeGivesBeanDefaultedName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NamedActionBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, NamedActionBean.class);
        assertEquals("namedActionBean", bean.getName());
    }

    @Test
    @DisplayName("2.8.1.3 - @Named declared by stereotype is not a bean qualifier")
    void namedDeclaredByStereotypeIsNotABeanQualifier() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NamedActionBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, NamedActionBean.class);
        boolean hasNamedQualifier = bean.getQualifiers().stream()
                .anyMatch(annotation -> annotation.annotationType().equals(jakarta.inject.Named.class));
        assertEquals(false, hasNamedQualifier);
    }

    @Test
    @DisplayName("2.8.1.3 - Non-empty @Named on stereotype is a definition error")
    void nonEmptyNamedOnStereotypeIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidNamedActionBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8 - Stereotype declaring qualifier other than @Named is non-portable")
    void stereotypeDeclaringQualifierOtherThanNamedIsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidQualifiedStereotypeBean.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8 - Stereotype annotated with @Typed is non-portable")
    void stereotypeAnnotatedWithTypedIsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidTypedStereotypeBean.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8.1.4 - @Alternative declared by stereotype marks bean as alternative")
    void alternativeDeclaredByStereotypeMarksBeanAsAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StrategyClient.class);
        syringe.setup();

        Bean<?> mockBean = findBean(syringe, MockStrategy.class);
        assertTrue(mockBean.isAlternative());

        StrategyClient client = syringe.inject(StrategyClient.class);
        assertEquals("default", client.strategyKind());
    }

    @Test
    @DisplayName("2.8.1.5 - @Priority on stereotype enables an alternative bean")
    void priorityOnStereotypeEnablesAlternativeBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), EnabledServiceClient.class);
        syringe.setup();

        EnabledServiceClient client = syringe.inject(EnabledServiceClient.class);
        assertEquals("stereotypePriorityAlternative", client.selectedServiceType());
    }

    @Test
    @DisplayName("2.8.1.5 - @Priority on stereotype participates in alternative ordering")
    void priorityOnStereotypeParticipatesInAlternativeOrdering() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), OrderedServiceClient.class);
        syringe.setup();

        OrderedServiceClient client = syringe.inject(OrderedServiceClient.class);
        assertEquals("highPriorityStereotypeAlternative", client.selectedServiceType());
    }

    @Test
    @DisplayName("2.8.1.5 - Different stereotype priorities require explicit bean @Priority")
    void differentStereotypePrioritiesRequireExplicitBeanPriority() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConflictingPriorityBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8.1.5 - Explicit bean @Priority overrides stereotype priorities")
    void explicitBeanPriorityOverridesStereotypePriorities() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ExplicitPriorityClient.class);
        syringe.setup();

        ExplicitPriorityClient client = syringe.inject(ExplicitPriorityClient.class);
        assertEquals("competingClassPriorityBean", client.selectedServiceType());
    }

    @Test
    @DisplayName("2.8.1.6 - Stereotype declarations are transitive")
    void stereotypeDeclarationsAreTransitive() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DeepAuditableActionBean.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, DeepAuditableActionBean.class);
        assertEquals(RequestScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("2.8.1.6 - @Target(TYPE) stereotype cannot be applied to broader-target stereotype")
    void targetTypeStereotypeCannotBeAppliedToBroaderTargetStereotype() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidTargetStereotypeBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8.2 - Stereotypes can be applied to bean class, producer method and producer field")
    void stereotypesCanBeAppliedToBeanClassProducerMethodAndProducerField() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LocationLoginAction.class);
        syringe.setup();

        Bean<?> loginActionBean = findBean(syringe, LocationLoginAction.class);
        assertEquals(RequestScoped.class, loginActionBean.getScope());

        ProducerBean<?> methodProducedBean = findProducerBeanByProducedType(syringe, LocationMethodProduct.class);
        ProducerBean<?> fieldProducedBean = findProducerBeanByProducedType(syringe, LocationFieldProduct.class);
        assertEquals(RequestScoped.class, methodProducedBean.getScope());
        assertEquals(RequestScoped.class, fieldProducedBean.getScope());
    }

    @Test
    @DisplayName("2.8.2 - Bean scope overrides stereotype default scope")
    void beanScopeOverridesStereotypeDefaultScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), OverrideMockLoginAction.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, OverrideMockLoginAction.class);
        assertEquals(ApplicationScoped.class, bean.getScope());
    }

    @Test
    @DisplayName("2.8.2 - Multiple stereotypes may be applied to the same bean")
    void multipleStereotypesMayBeAppliedToSameBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MultiStereotypeLoginAction.class);
        syringe.setup();

        Bean<?> bean = findBean(syringe, MultiStereotypeLoginAction.class);
        assertTrue(bean.getStereotypes().contains(MultiAction.class));
        assertTrue(bean.getStereotypes().contains(DaoStereotype.class));
        assertEquals(RequestScoped.class, bean.getScope());
    }

    private Bean<?> findBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(bean -> bean.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bean not found: " + beanClass.getName()));
    }

    private ProducerBean<?> findProducerBeanByProducedType(Syringe syringe, Class<?> producedType) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getTypes().contains(producedType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Producer bean not found for type: " + producedType.getName()));
    }

}
