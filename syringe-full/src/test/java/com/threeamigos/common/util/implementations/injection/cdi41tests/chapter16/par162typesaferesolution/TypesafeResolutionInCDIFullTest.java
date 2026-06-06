package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter16.par162typesaferesolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("16.2 - Typesafe resolution in CDI Full test")
public class TypesafeResolutionInCDIFullTest {

    @Test
    @DisplayName("16.2.1 - A parameterized bean type matches a raw required type when assignable")
    void shouldMatchParameterizedBeanTypeToRawRequiredType() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RawListClient.class);
        syringe.exclude(ParameterizedListClient.class, CustomQualifiedClient.class,
                UnsatisfiedDelegateDecorator.class, AmbiguousDelegateDecorator.class,
                AmbiguousDelegateServiceOne.class, AmbiguousDelegateServiceTwo.class);
        syringe.setup();

        RawListClient client = syringe.inject(RawListClient.class);
        assertEquals("alpha", client.firstValue());
    }

    @Test
    @DisplayName("16.2.1 - Identical parameterized required and bean types match")
    void shouldMatchIdenticalParameterizedTypes() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ParameterizedListClient.class);
        syringe.exclude(RawListClient.class, CustomQualifiedClient.class,
                UnsatisfiedDelegateDecorator.class, AmbiguousDelegateDecorator.class,
                AmbiguousDelegateServiceOne.class, AmbiguousDelegateServiceTwo.class);
        syringe.setup();

        ParameterizedListClient client = syringe.inject(ParameterizedListClient.class);
        assertEquals("alpha", client.firstValue());
    }

    @Test
    @DisplayName("16.2.1 - For a custom Bean implementation, getTypes() and getQualifiers() drive resolution")
    void shouldResolveCustomBeanUsingTypesAndQualifiers() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), CustomQualifiedClient.class);
        syringe.exclude(RawListClient.class, ParameterizedListClient.class, TypedListProducer.class,
                UnsatisfiedDelegateDecorator.class, AmbiguousDelegateDecorator.class,
                AmbiguousDelegateServiceOne.class, AmbiguousDelegateServiceTwo.class);
        syringe.getKnowledgeBase().addBean(new CustomQualifiedSyntheticBean());
        syringe.setup();

        CustomQualifiedClient client = syringe.inject(CustomQualifiedClient.class);
        assertEquals("custom-qualified", client.serviceId());
    }

    @Test
    @DisplayName("16.2.2 - Unsatisfied decorator delegate injection point is a deployment problem")
    void shouldFailDeploymentForUnsatisfiedDecoratorDelegateInjectionPoint() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), UnsatisfiedDelegateDecorator.class);
        syringe.exclude(CustomQualifiedClient.class, AmbiguousDelegateDecorator.class,
                AmbiguousDelegateServiceOne.class, AmbiguousDelegateServiceTwo.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        InjectionPoint delegateInjectionPoint = delegateInjectionPointFrom(UnsatisfiedDelegateDecorator.class, "delegate");
        assertThrows(UnsatisfiedResolutionException.class, () -> beanManager.validate(delegateInjectionPoint));
    }

    @Test
    @DisplayName("16.2.2 - Ambiguous decorator delegate injection point is a deployment problem")
    void shouldFailDeploymentForAmbiguousDecoratorDelegateInjectionPoint() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AmbiguousDelegateDecorator.class);
        syringe.exclude(CustomQualifiedClient.class, UnsatisfiedDelegateDecorator.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        InjectionPoint delegateInjectionPoint = delegateInjectionPointFrom(AmbiguousDelegateDecorator.class, "delegate");
        assertThrows(AmbiguousResolutionException.class, () -> beanManager.validate(delegateInjectionPoint));
    }

    @Test
    @DisplayName("16.2.2 - For a custom Bean implementation, getInjectionPoints() drives dependency validation")
    void shouldUseCustomBeanInjectionPointsDuringValidation() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), CustomBeanValidationAnchor.class);
        syringe.exclude(RawListClient.class, ParameterizedListClient.class, TypedListProducer.class,
                CustomQualifiedClient.class, UnsatisfiedDelegateDecorator.class,
                AmbiguousDelegateDecorator.class, AmbiguousDelegateServiceOne.class,
                AmbiguousDelegateServiceTwo.class);
        syringe.getKnowledgeBase().addBean(new CustomBeanWithInjectionPoints());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("16.2.4 - Ambiguous name resolves by eliminating non-alternatives when selected alternative producer method exists")
    void shouldResolveAmbiguousNameUsingSelectedAlternativeProducerMethod() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), Par1624DefaultMethodNamedService.class);
        syringe.exclude(RawListClient.class, ParameterizedListClient.class, TypedListProducer.class,
                CustomQualifiedClient.class, UnsatisfiedDelegateDecorator.class,
                AmbiguousDelegateDecorator.class, AmbiguousDelegateServiceOne.class,
                AmbiguousDelegateServiceTwo.class, Par1624DefaultFieldNamedService.class,
                Par1624AlternativeFieldProducerFactory.class);
        syringe.enableAlternative(Par1624AlternativeMethodProducerFactory.class);
        syringe.setup();

        Set<Bean<?>> namedBeans = syringe.getBeanManager().getBeans("par1624MethodName");
        assertTrue(namedBeans.size() > 1);
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) namedBeans);
        Par1624NamedService service = (Par1624NamedService) syringe.getBeanManager().getReference(
                resolved,
                Par1624NamedService.class,
                syringe.getBeanManager().createCreationalContext(resolved));
        assertEquals("alternative-method-producer", service.id());
    }

    @Test
    @DisplayName("16.2.4 - Ambiguous name resolves by eliminating non-alternatives when selected alternative producer field exists")
    void shouldResolveAmbiguousNameUsingSelectedAlternativeProducerField() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), Par1624DefaultFieldNamedService.class);
        syringe.exclude(RawListClient.class, ParameterizedListClient.class, TypedListProducer.class,
                CustomQualifiedClient.class, UnsatisfiedDelegateDecorator.class,
                AmbiguousDelegateDecorator.class, AmbiguousDelegateServiceOne.class,
                AmbiguousDelegateServiceTwo.class, Par1624DefaultMethodNamedService.class,
                Par1624AlternativeMethodProducerFactory.class);
        syringe.enableAlternative(Par1624AlternativeFieldProducerFactory.class);
        syringe.setup();

        Set<Bean<?>> namedBeans = syringe.getBeanManager().getBeans("par1624FieldName");
        assertTrue(namedBeans.size() > 1);
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) namedBeans);
        Par1624NamedService service = (Par1624NamedService) syringe.getBeanManager().getReference(
                resolved,
                Par1624NamedService.class,
                syringe.getBeanManager().createCreationalContext(resolved));
        assertEquals("alternative-field-producer", service.id());
    }

    @Test
    @DisplayName("16.2.4 - Non-selected alternative producer is eliminated and does not win ambiguous name resolution")
    void shouldNotUseUnselectedAlternativeProducerForAmbiguousName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), Par1624DefaultMethodNamedService.class);
        syringe.exclude(RawListClient.class, ParameterizedListClient.class, TypedListProducer.class,
                CustomQualifiedClient.class, UnsatisfiedDelegateDecorator.class,
                AmbiguousDelegateDecorator.class, AmbiguousDelegateServiceOne.class,
                AmbiguousDelegateServiceTwo.class, Par1624DefaultFieldNamedService.class,
                Par1624AlternativeFieldProducerFactory.class);
        syringe.setup();

        Set<Bean<?>> namedBeans = syringe.getBeanManager().getBeans("par1624MethodName");
        assertEquals(1, namedBeans.size());
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) namedBeans);
        Par1624NamedService service = (Par1624NamedService) syringe.getBeanManager().getReference(
                resolved,
                Par1624NamedService.class,
                syringe.getBeanManager().createCreationalContext(resolved));
        assertEquals("default-method-named", service.id());
    }

    @Qualifier
    @Target({FIELD, METHOD, PARAMETER})
    @Retention(RUNTIME)
    public @interface Par162List {
    }

    @Qualifier
    @Target({TYPE, FIELD, METHOD, PARAMETER})
    @Retention(RUNTIME)
    public @interface Par162Custom {
    }

    @Qualifier
    @Target({TYPE, FIELD, METHOD, PARAMETER})
    @Retention(RUNTIME)
    public @interface Par162DelegateAmbiguous {
    }

    @Qualifier
    @Target({TYPE, FIELD, METHOD, PARAMETER})
    @Retention(RUNTIME)
    public @interface Par162DelegateUnsatisfied {
    }

    @Dependent
    public static class TypedListProducer {
        @Produces
        @Par162List
        public List<String> stringList() {
            return Arrays.asList("alpha", "beta");
        }
    }

    @Dependent
    public static class RawListClient {
        @Inject
        @Par162List
        List rawList;

        String firstValue() {
            return rawList.get(0).toString();
        }
    }

    @Dependent
    public static class ParameterizedListClient {
        @Inject
        @Par162List
        List<String> stringList;

        String firstValue() {
            return stringList.get(0);
        }
    }

    public interface CustomQualifiedService {
        String id();
    }

    @Dependent
    public static class CustomQualifiedClient {
        @Inject
        @Par162Custom
        CustomQualifiedService service;

        String serviceId() {
            return service.id();
        }
    }

    public static class NotTheResolvedBeanClass {
    }

    public interface DecoratedContract {
        String decorate();
    }

    public interface Par1624NamedService {
        String id();
    }

    @Dependent
    @Named("par1624MethodName")
    public static class Par1624DefaultMethodNamedService implements Par1624NamedService {
        @Override
        public String id() {
            return "default-method-named";
        }
    }

    @Alternative
    @Dependent
    public static class Par1624AlternativeMethodProducerFactory {
        @Produces
        @Named("par1624MethodName")
        public Par1624NamedService namedService() {
            return new Par1624NamedService() {
                @Override
                public String id() {
                    return "alternative-method-producer";
                }
            };
        }
    }

    @Dependent
    @Named("par1624FieldName")
    public static class Par1624DefaultFieldNamedService implements Par1624NamedService {
        @Override
        public String id() {
            return "default-field-named";
        }
    }

    @Alternative
    @Dependent
    public static class Par1624AlternativeFieldProducerFactory {
        @Produces
        @Named("par1624FieldName")
        private final Par1624NamedService namedService = new Par1624NamedService() {
            @Override
            public String id() {
                return "alternative-field-producer";
            }
        };
    }

    @Dependent
    @Par162DelegateAmbiguous
    public static class AmbiguousDelegateServiceOne implements DecoratedContract {
        @Override
        public String decorate() {
            return "one";
        }
    }

    @Dependent
    @Par162DelegateAmbiguous
    public static class AmbiguousDelegateServiceTwo implements DecoratedContract {
        @Override
        public String decorate() {
            return "two";
        }
    }

    @Decorator
    @Dependent
    public static class AmbiguousDelegateDecorator implements DecoratedContract {
        @Inject
        @Delegate
        @Par162DelegateAmbiguous
        DecoratedContract delegate;

        @Override
        public String decorate() {
            return delegate.decorate();
        }
    }

    @Decorator
    @Dependent
    public static class UnsatisfiedDelegateDecorator implements DecoratedContract {
        @Inject
        @Delegate
        @Par162DelegateUnsatisfied
        DecoratedContract delegate;

        @Override
        public String decorate() {
            return delegate.decorate();
        }
    }

    @Dependent
    public static class CustomBeanValidationAnchor {
    }

    public interface MissingCustomDependency {
    }

    public static class CustomBeanInjectionSite {
        @Inject
        MissingCustomDependency dependency;
    }

    private static final class Par162CustomLiteral extends AnnotationLiteral<Par162Custom> implements Par162Custom {
    }

    private static final class AnyLiteral extends AnnotationLiteral<Any> implements Any {
    }

    private static final class DefaultLiteral extends AnnotationLiteral<Default> implements Default {
    }

    public static class CustomQualifiedSyntheticBean implements Bean<CustomQualifiedService> {
        private static final Set<Type> TYPES;
        private static final Set<java.lang.annotation.Annotation> QUALIFIERS;

        static {
            Set<Type> types = new HashSet<Type>();
            types.add(CustomQualifiedService.class);
            types.add(Object.class);
            TYPES = Collections.unmodifiableSet(types);

            Set<java.lang.annotation.Annotation> qualifiers = new HashSet<java.lang.annotation.Annotation>();
            qualifiers.add(new Par162CustomLiteral());
            qualifiers.add(new AnyLiteral());
            QUALIFIERS = Collections.unmodifiableSet(qualifiers);
        }

        @Override
        public CustomQualifiedService create(CreationalContext<CustomQualifiedService> creationalContext) {
            return new CustomQualifiedService() {
                @Override
                public String id() {
                    return "custom-qualified";
                }
            };
        }

        @Override
        public void destroy(CustomQualifiedService instance, CreationalContext<CustomQualifiedService> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        @Override
        public Class<?> getBeanClass() {
            return NotTheResolvedBeanClass.class;
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
        public Set<java.lang.annotation.Annotation> getQualifiers() {
            return QUALIFIERS;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends java.lang.annotation.Annotation>> getStereotypes() {
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

    public static class CustomBeanWithInjectionPoints implements Bean<Object> {
        private static final Set<Type> TYPES;
        private static final Set<java.lang.annotation.Annotation> QUALIFIERS;
        private final Set<InjectionPoint> injectionPoints;

        static {
            Set<Type> types = new HashSet<Type>();
            types.add(Object.class);
            TYPES = Collections.unmodifiableSet(types);

            Set<java.lang.annotation.Annotation> qualifiers = new HashSet<java.lang.annotation.Annotation>();
            qualifiers.add(new DefaultLiteral());
            qualifiers.add(new AnyLiteral());
            QUALIFIERS = Collections.unmodifiableSet(qualifiers);
        }

        public CustomBeanWithInjectionPoints() {
            try {
                Field field = CustomBeanInjectionSite.class.getDeclaredField("dependency");
                injectionPoints = Collections.<InjectionPoint>singleton(new InjectionPointImpl<Object>(field, this));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Failed to create injection point metadata", e);
            }
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

        @Override
        public Class<?> getBeanClass() {
            return CustomBeanInjectionSite.class;
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
        public Set<java.lang.annotation.Annotation> getQualifiers() {
            return QUALIFIERS;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends java.lang.annotation.Annotation>> getStereotypes() {
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

    private static InjectionPoint delegateInjectionPointFrom(Class<?> decoratorClass, String fieldName) {
        try {
            Field field = decoratorClass.getDeclaredField(fieldName);
            return new InjectionPointImpl<Object>(field, null);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Delegate field not found: " + decoratorClass.getName() + "." + fieldName, e);
        }
    }
}
