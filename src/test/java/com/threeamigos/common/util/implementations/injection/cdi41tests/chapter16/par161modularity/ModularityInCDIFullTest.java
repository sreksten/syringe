package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter16.par161modularity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Specializes;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("16.1 - Modularity in CDI Full test")
public class ModularityInCDIFullTest {

    @Test
    @DisplayName("16.1 - A library can be treated as an explicit bean archive")
    void shouldAllowExplicitBeanArchiveLibrary() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PlainLibraryService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        assertEquals(BeanArchiveMode.EXPLICIT,
                syringe.getKnowledgeBase().getBeanArchiveMode(PlainLibraryService.class));
        assertTrue(hasBean(syringe, PlainLibraryService.class));
    }

    @Test
    @DisplayName("16.1 - A library can be treated as an implicit bean archive")
    void shouldAllowImplicitBeanArchiveLibrary() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ImplicitArchiveAnchor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.IMPLICIT);
        syringe.setup();

        assertEquals(BeanArchiveMode.IMPLICIT,
                syringe.getKnowledgeBase().getBeanArchiveMode(ImplicitArchiveAnchor.class));
        assertTrue(hasBean(syringe, ImplicitArchiveAnchor.class));
        assertFalse(hasBean(syringe, PlainLibraryService.class));
    }

    @Test
    @DisplayName("16.1 - An alternative is not available unless explicitly selected")
    void shouldNotUseAlternativeWhenNotExplicitlySelected() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ServiceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        ServiceClient client = syringe.inject(ServiceClient.class);
        assertEquals("default", client.serviceId());
    }

    @Test
    @DisplayName("16.1 - Selected alternative is available when the module is a bean archive")
    void shouldUseSelectedAlternativeWhenModuleIsBeanArchive() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ServiceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(ModuleAlternativeService.class);
        syringe.setup();

        ServiceClient client = syringe.inject(ServiceClient.class);
        assertEquals("alternative", client.serviceId());
    }

    @Test
    @DisplayName("16.1 - Selected alternative is unavailable when the module is not a bean archive")
    void shouldNotExposeAlternativeWhenModuleIsNotBeanArchive() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ServiceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.NONE);
        syringe.enableAlternative(ModuleAlternativeService.class);
        syringe.setup();

        assertFalse(hasBean(syringe, ModuleAlternativeService.class));
        assertFalse(hasBean(syringe, DefaultModuleService.class));
        assertFalse(hasBean(syringe, ServiceClient.class));
    }

    @Test
    @DisplayName("16.1.1.1 - Alternative custom bean implementing Prioritized is enabled for the application with declared priority")
    void shouldEnablePrioritizedAlternativeForEntireApplication() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrioritizedServiceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        PrioritizedServiceClient client = syringe.inject(PrioritizedServiceClient.class);
        assertEquals("prioritized-alternative", client.serviceId());

        BeanImpl<?> alternativeBean = findBeanImpl(syringe, PrioritizedAlternativeService.class);
        assertTrue(alternativeBean.isAlternativeEnabled());
        assertEquals(Integer.valueOf(250), alternativeBean.getPriority());
    }

    @Test
    @DisplayName("16.1.1.2 - Alternative managed bean class listed in beans.xml is selected for the bean archive")
    void shouldSelectManagedBeanAlternativeListedUnderAlternativesClass() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), XmlManagedClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlAlternatives(syringe,
                "<class>" + XmlManagedAlternativeService.class.getName() + "</class>",
                "");
        syringe.setup();

        XmlManagedClient client = syringe.inject(XmlManagedClient.class);
        assertEquals("xml-managed-alternative", client.serviceId());
    }

    @Test
    @DisplayName("16.1.1.2 - Alternative producer is selected when its declaring bean class is listed in beans.xml")
    void shouldSelectAlternativeProducerWhenDeclaringClassIsListed() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), XmlProducerClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlAlternatives(syringe,
                "<class>" + XmlProducerFactory.class.getName() + "</class>",
                "");
        syringe.setup();

        XmlProducerClient client = syringe.inject(XmlProducerClient.class);
        assertEquals("xml-producer-alternative", client.serviceId());
    }

    @Test
    @DisplayName("16.1.1.2 - Alternative is selected when an @Alternative stereotype is listed in beans.xml")
    void shouldSelectAlternativeWhenAlternativeStereotypeIsListed() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), XmlStereotypeClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlAlternatives(syringe,
                "",
                "<stereotype>" + XmlMockAlternative.class.getName() + "</stereotype>");
        syringe.setup();

        XmlStereotypeClient client = syringe.inject(XmlStereotypeClient.class);
        assertEquals("xml-stereotype-alternative", client.serviceId());
    }

    @Test
    @DisplayName("16.1.1.2 - Invalid <class> alternative entry is a deployment problem")
    void shouldTreatInvalidAlternativeClassEntryAsDeploymentProblem() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), XmlManagedClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlAlternatives(syringe,
                "<class>" + PlainLibraryService.class.getName() + "</class>",
                "");

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.1.1.2 - Invalid <stereotype> entry is a deployment problem")
    void shouldTreatInvalidAlternativeStereotypeEntryAsDeploymentProblem() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), XmlStereotypeClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlAlternatives(syringe,
                "",
                "<stereotype>" + NotAlternativeStereotype.class.getName() + "</stereotype>");

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.1.1.2 - Duplicate alternatives entries in beans.xml are a deployment problem")
    void shouldTreatDuplicateAlternativeEntriesAsDeploymentProblem() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), XmlManagedClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        String alternativeClass = XmlManagedAlternativeService.class.getName();
        addBeansXmlAlternatives(syringe,
                "<class>" + alternativeClass + "</class>" +
                        "<class>" + alternativeClass + "</class>",
                "");

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.1.2 - A bean not deployed in a bean archive is disabled")
    void shouldDisableBeanWhenNotDeployedInBeanArchive() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ArchiveEnabledProbe.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.NONE);
        syringe.setup();

        assertFalse(hasBean(syringe, ArchiveEnabledProbe.class));
    }

    @Test
    @DisplayName("16.1.2 - Producer method of a disabled bean is disabled")
    void shouldDisableProducerOfDisabledBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DisabledProducerClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBean(syringe, DisabledAlternativeProducerFactory.class);

        DisabledProducerClient client = syringe.inject(DisabledProducerClient.class);
        assertEquals("default-produced", client.serviceId());

        assertFalse(producerBean.isAlternativeEnabled());
    }

    @Test
    @DisplayName("16.1.2 - A bean specialized by another enabled bean is disabled")
    void shouldDisableSpecializedBeanWhenSpecializingBeanIsEnabled() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SpecializationRuleClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        SpecializationRuleClient client = syringe.inject(SpecializationRuleClient.class);
        assertEquals("specializing", client.serviceId());
    }

    @Test
    @DisplayName("16.1.2 - An unselected alternative bean is disabled")
    void shouldDisableUnselectedAlternativeBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ServiceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        ServiceClient client = syringe.inject(ServiceClient.class);
        assertEquals("default", client.serviceId());

        BeanImpl<?> alternativeBean = findBeanImpl(syringe, ModuleAlternativeService.class);
        assertFalse(alternativeBean.isAlternativeEnabled());
    }

    @Test
    @DisplayName("16.1.2 - A selected alternative bean is enabled")
    void shouldEnableSelectedAlternativeBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ServiceClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(ModuleAlternativeService.class);
        syringe.setup();

        ServiceClient client = syringe.inject(ServiceClient.class);
        assertEquals("alternative", client.serviceId());

        BeanImpl<?> alternativeBean = findBeanImpl(syringe, ModuleAlternativeService.class);
        assertTrue(alternativeBean.isAlternativeEnabled());
    }

    @Test
    @DisplayName("16.1.3 - Inconsistent specialization is a deployment problem when two enabled beans specialize the same bean")
    void shouldFailForInconsistentSpecialization() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InconsistentSpecializationClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(FirstSpecializingAlternative.class);
        syringe.enableAlternative(SecondSpecializingAlternative.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.1.3 - InconsistentSpecializationTest: deployment problem when two enabled beans specialize the same managed bean")
    void shouldMatchTckInconsistentSpecializationDeploymentFailure() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InconsistentSpecializationClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(FirstSpecializingAlternative.class);
        syringe.enableAlternative(SecondSpecializingAlternative.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.1.3 - Specialization is consistent when only one specializing bean is enabled")
    void shouldAllowSpecializationWhenOnlyOneSpecializingBeanIsEnabled() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InconsistentSpecializationClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(FirstSpecializingAlternative.class);
        syringe.setup();

        InconsistentSpecializationClient client = syringe.inject(InconsistentSpecializationClient.class);
        assertEquals("first-specializer", client.serviceId());
    }

    @Test
    @DisplayName("16.1.4 - A non-interceptor, non-decorator enabled bean is available for injection")
    void shouldMakeEnabledNonInterceptorNonDecoratorBeanAvailableForInjection() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AvailabilityClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        AvailabilityClient client = syringe.inject(AvailabilityClient.class);
        assertEquals("default-availability", client.serviceId());
    }

    @Test
    @DisplayName("16.1.4 - Interceptors and decorators are not available for injection")
    void shouldNotMakeInterceptorOrDecoratorAvailableForInjection() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AvailabilityClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(beanManager.getBeans(AvailabilityInterceptor.class).isEmpty());
        assertTrue(beanManager.getBeans(AvailabilityDecorator.class).isEmpty());
    }

    @Test
    @DisplayName("16.1.4 - Unselected alternative is not available for injection in module")
    void shouldNotUseUnselectedAlternativeInModule() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AvailabilityClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        AvailabilityClient client = syringe.inject(AvailabilityClient.class);
        assertEquals("default-availability", client.serviceId());
    }

    @Test
    @DisplayName("16.1.4 - Alternative selected for bean archive is available for injection in module")
    void shouldUseAlternativeSelectedForBeanArchiveInModule() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AvailabilityClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlAlternatives(syringe,
                "<class>" + ArchiveSelectedAvailabilityAlternative.class.getName() + "</class>",
                "");
        syringe.setup();

        AvailabilityClient client = syringe.inject(AvailabilityClient.class);
        assertEquals("archive-selected-alternative", client.serviceId());
    }

    @Test
    @DisplayName("16.1.4 - Alternative selected for application is available for injection in module")
    void shouldUseAlternativeSelectedForApplicationInModule() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AvailabilityClient.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(AppSelectedAvailabilityAlternative.class);
        syringe.setup();

        AvailabilityClient client = syringe.inject(AvailabilityClient.class);
        assertEquals("app-selected-alternative", client.serviceId());
    }

    @Test
    @DisplayName("16.1.4 - Bean class not accessible to the injection point declaring class is not available for injection")
    void shouldNotUseBeanWhoseClassIsNotAccessibleToInjectionPointDeclaringClass() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ModuleAccessibility16_1_4Client.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.enableAlternative(InaccessibleAvailabilityAlternative.class);
        syringe.setup();

        ModuleAccessibility16_1_4Client client = syringe.inject(ModuleAccessibility16_1_4Client.class);
        assertEquals("accessible-default", client.serviceId());
    }

    private static boolean hasBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .anyMatch(bean -> beanClass.equals(bean.getBeanClass()));
    }

    private static BeanImpl<?> findBeanImpl(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(bean -> beanClass.equals(bean.getBeanClass()))
                .filter(BeanImpl.class::isInstance)
                .map(BeanImpl.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bean not found: " + beanClass.getName()));
    }

    private static ProducerBean<?> findProducerBean(Syringe syringe, Class<?> declaringClass) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(bean -> declaringClass.equals(bean.getBeanClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Producer bean not found: " + declaringClass.getName()));
    }

    private static void addBeansXmlAlternatives(Syringe syringe, String classEntries, String stereotypeEntries) {
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<alternatives>" + classEntries + stereotypeEntries + "</alternatives>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    public interface ModuleService {
        String id();
    }

    @Dependent
    public static class DefaultModuleService implements ModuleService {
        @Override
        public String id() {
            return "default";
        }
    }

    @Alternative
    @Dependent
    public static class ModuleAlternativeService implements ModuleService {
        @Override
        public String id() {
            return "alternative";
        }
    }

    @Dependent
    public static class ServiceClient {
        @Inject
        ModuleService moduleService;

        String serviceId() {
            return moduleService.id();
        }
    }

    @Dependent
    public static class ImplicitArchiveAnchor {
    }

    public static class PlainLibraryService {
        public String id() {
            return "plain";
        }
    }

    public interface PrioritizedService {
        String id();
    }

    @Dependent
    public static class DefaultPrioritizedService implements PrioritizedService {
        @Override
        public String id() {
            return "default-prioritized";
        }
    }

    @Alternative
    @Dependent
    public static class PrioritizedAlternativeService implements PrioritizedService, Prioritized {
        @Override
        public int getPriority() {
            return 250;
        }

        @Override
        public String id() {
            return "prioritized-alternative";
        }
    }

    @Dependent
    public static class PrioritizedServiceClient {
        @Inject
        PrioritizedService prioritizedService;

        String serviceId() {
            return prioritizedService.id();
        }
    }

    public interface XmlManagedService {
        String id();
    }

    @Dependent
    public static class XmlDefaultManagedService implements XmlManagedService {
        @Override
        public String id() {
            return "xml-default-managed";
        }
    }

    @Alternative
    @Dependent
    public static class XmlManagedAlternativeService implements XmlManagedService {
        @Override
        public String id() {
            return "xml-managed-alternative";
        }
    }

    @Dependent
    public static class XmlManagedClient {
        @Inject
        XmlManagedService service;

        String serviceId() {
            return service.id();
        }
    }

    public interface XmlProducedService {
        String id();
    }

    @Dependent
    public static class XmlDefaultProducedService implements XmlProducedService {
        @Override
        public String id() {
            return "xml-default-produced";
        }
    }

    @Dependent
    public static class XmlProducerFactory {
        @Produces
        @Alternative
        public XmlProducedService produceAlternative() {
            return new XmlProducedService() {
                @Override
                public String id() {
                    return "xml-producer-alternative";
                }
            };
        }
    }

    @Dependent
    public static class XmlProducerClient {
        @Inject
        XmlProducedService service;

        String serviceId() {
            return service.id();
        }
    }

    public interface XmlStereotypeService {
        String id();
    }

    @Dependent
    public static class XmlDefaultStereotypeService implements XmlStereotypeService {
        @Override
        public String id() {
            return "xml-default-stereotype";
        }
    }

    @Alternative
    @Stereotype
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface XmlMockAlternative {
    }

    @Stereotype
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface NotAlternativeStereotype {
    }

    @XmlMockAlternative
    @Dependent
    public static class XmlStereotypeAlternativeService implements XmlStereotypeService {
        @Override
        public String id() {
            return "xml-stereotype-alternative";
        }
    }

    @Dependent
    public static class XmlStereotypeClient {
        @Inject
        XmlStereotypeService service;

        String serviceId() {
            return service.id();
        }
    }

    @Dependent
    public static class ArchiveEnabledProbe {
    }

    public interface DisabledProducedService {
        String id();
    }

    @Dependent
    public static class DefaultProducedServiceFactory {
        @Produces
        public DisabledProducedService produceDefault() {
            return new DisabledProducedService() {
                @Override
                public String id() {
                    return "default-produced";
                }
            };
        }
    }

    @Alternative
    @Dependent
    public static class DisabledAlternativeProducerFactory {
        @Produces
        public DisabledProducedService produceAlternative() {
            return new DisabledProducedService() {
                @Override
                public String id() {
                    return "alternative-produced";
                }
            };
        }
    }

    @Dependent
    public static class DisabledProducerClient {
        @Inject
        DisabledProducedService service;

        String serviceId() {
            return service.id();
        }
    }

    public interface SpecializedRuleService {
        String id();
    }

    @Dependent
    public static class BaseSpecializedRuleService implements SpecializedRuleService {
        @Override
        public String id() {
            return "base";
        }
    }

    @Alternative
    @Specializes
    @Priority(50)
    @Dependent
    public static class EnabledSpecializingRuleService extends BaseSpecializedRuleService {
        @Override
        public String id() {
            return "specializing";
        }
    }

    @Dependent
    public static class SpecializationRuleClient {
        @Inject
        SpecializedRuleService service;

        String serviceId() {
            return service.id();
        }
    }

    public interface InconsistentSpecializationService {
        String id();
    }

    @Dependent
    public static class BaseInconsistentSpecializationService implements InconsistentSpecializationService {
        @Override
        public String id() {
            return "base-inconsistent";
        }
    }

    @Alternative
    @Specializes
    @Dependent
    public static class FirstSpecializingAlternative extends BaseInconsistentSpecializationService {
        @Override
        public String id() {
            return "first-specializer";
        }
    }

    @Alternative
    @Specializes
    @Dependent
    public static class SecondSpecializingAlternative extends BaseInconsistentSpecializationService {
        @Override
        public String id() {
            return "second-specializer";
        }
    }

    @Dependent
    public static class InconsistentSpecializationClient {
        @Inject
        InconsistentSpecializationService service;

        String serviceId() {
            return service.id();
        }
    }

    public interface AvailabilityService {
        String id();
    }

    @Dependent
    public static class DefaultAvailabilityService implements AvailabilityService {
        @Override
        public String id() {
            return "default-availability";
        }
    }

    @Alternative
    @Dependent
    public static class ArchiveSelectedAvailabilityAlternative implements AvailabilityService {
        @Override
        public String id() {
            return "archive-selected-alternative";
        }
    }

    @Alternative
    @Dependent
    public static class AppSelectedAvailabilityAlternative implements AvailabilityService {
        @Override
        public String id() {
            return "app-selected-alternative";
        }
    }

    @Dependent
    public static class AvailabilityClient {
        @Inject
        AvailabilityService service;

        String serviceId() {
            return service.id();
        }
    }

    @InterceptorBinding
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface AvailabilityTracked {
    }

    @AvailabilityTracked
    @Interceptor
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 30)
    public static class AvailabilityInterceptor {
        @AroundInvoke
        public Object around(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }
    }

    @Decorator
    public static class AvailabilityDecorator implements AvailabilityService {
        @Inject
        @Delegate
        AvailabilityService delegate;

        @Override
        public String id() {
            return delegate.id();
        }
    }

    public interface AccessibilityContract {
        String id();
    }

    @Dependent
    public static class AccessibleAvailabilityBean implements AccessibilityContract {
        @Override
        public String id() {
            return "accessible-default";
        }
    }

    @Alternative
    @Dependent
    private static class InaccessibleAvailabilityAlternative implements AccessibilityContract {
        @Override
        public String id() {
            return "inaccessible-alternative";
        }
    }
}
