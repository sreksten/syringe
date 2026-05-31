package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par203decoratorresolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("20.3 - Decorator resolution")
@Execution(ExecutionMode.SAME_THREAD)
public class DecoratorResolutionTest {

    @Test
    @DisplayName("20.3 - A decorator is bound when bean is assignable to delegate injection point")
    void shouldBindDecoratorWhenBeanAssignableToDelegateType() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(StringServiceBean.class, StringServiceDecorator.class);
        syringe.setup();

        StringService service = syringe.inject(StringService.class);
        assertEquals("decorated:value", service.value());
        assertEquals(Arrays.asList("decorator-before", "business", "decorator-after"), ResolutionRecorder.events());
    }

    @Test
    @DisplayName("20.3 - A decorator is bound only when enabled in the bean archive")
    void shouldApplyDecoratorOnlyWhenEnabledInBeanArchive() {
        ResolutionRecorder.reset();
        Syringe disabled = newSyringe(StringServiceBean.class, BeansXmlOnlyStringServiceDecorator.class);
        disabled.setup();
        assertEquals("value", disabled.inject(StringService.class).value());
        assertEquals(Collections.singletonList("business"), ResolutionRecorder.events());

        ResolutionRecorder.reset();
        Syringe enabled = newSyringe(StringServiceBean.class, BeansXmlOnlyStringServiceDecorator.class);
        addBeansXmlDecorators(enabled, BeansXmlOnlyStringServiceDecorator.class.getName());
        enabled.setup();
        assertEquals("beansxml:value", enabled.inject(StringService.class).value());
        assertEquals(Arrays.asList("beansxml-before", "business", "beansxml-after"), ResolutionRecorder.events());
    }

    @Test
    @DisplayName("20.3 - Delegate qualifiers participate in decorator binding")
    void shouldResolveDecoratorUsingDelegateQualifiers() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(
                QualifiedService.class,
                SpecialQualifiedServiceBean.class,
                PlainQualifiedServiceBean.class,
                SpecialQualifiedDecorator.class
        );
        syringe.setup();

        QualifiedService service = syringe.inject(QualifiedService.class, SpecialLiteral.INSTANCE);
        assertEquals("special-decorated:special", service.name());
        assertEquals(Arrays.asList("special-decorator-before", "special-business", "special-decorator-after"), ResolutionRecorder.events());
    }

    @Test
    @DisplayName("20.3 - If a decorator matches a managed bean, the managed bean type must be proxyable")
    void shouldFailDeploymentWhenDecoratedManagedBeanTypeIsUnproxyable() {
        Syringe syringe = newSyringe(UnproxyableContract.class, FinalUnproxyableService.class, UnproxyableDecorator.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.3 - For custom Decorator, container uses getDelegateType/getDelegateQualifiers/getDecoratedTypes")
    void shouldResolveCustomDecoratorMetadataViaDecoratorSpiMethods() {
        SyntheticDecoratorRecorder.reset();
        Syringe syringe = newSyringe(
                SyntheticDecoratorExtension.class,
                StringServiceBean.class
        );
        syringe.addExtension(SyntheticDecoratorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = beanManager.resolveDecorators(
                Collections.<Type>singleton(StringService.class),
                Default.Literal.INSTANCE
        );

        assertTrue(
                decorators.stream().anyMatch(d -> d.getBeanClass().equals(RecordingCustomDecorator.class)),
                "Custom decorator should be resolved via Decorator SPI metadata"
        );
        assertTrue(SyntheticDecoratorRecorder.delegateTypeCalls("recording") > 0);
        assertTrue(SyntheticDecoratorRecorder.delegateQualifierCalls("recording") > 0);
        assertTrue(SyntheticDecoratorRecorder.decoratedTypesCalls("recording") > 0);
    }

    @Test
    @DisplayName("20.3 - A custom Decorator implementing Prioritized is enabled application-wide with priority")
    void shouldEnablePrioritizedCustomDecoratorsWithPriorityOrdering() {
        SyntheticDecoratorRecorder.reset();
        Syringe syringe = newSyringe(
                PrioritizedSyntheticDecoratorExtension.class,
                StringServiceBean.class
        );
        syringe.addExtension(PrioritizedSyntheticDecoratorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = beanManager.resolveDecorators(
                Collections.<Type>singleton(StringService.class),
                Default.Literal.INSTANCE
        );

        List<Class<?>> resolvedClasses = new ArrayList<Class<?>>();
        for (jakarta.enterprise.inject.spi.Decorator<?> decorator : decorators) {
            resolvedClasses.add(decorator.getBeanClass());
        }

        int fast = resolvedClasses.indexOf(PrioritizedFastCustomDecorator.class);
        int slow = resolvedClasses.indexOf(PrioritizedSlowCustomDecorator.class);

        assertTrue(fast >= 0, "Fast prioritized custom decorator should be enabled");
        assertTrue(slow >= 0, "Slow prioritized custom decorator should be enabled");
        assertTrue(fast < slow, "Lower priority value must be resolved first");
    }

    @Test
    @DisplayName("20.3.1 - Raw bean type is assignable to parameterized delegate type when delegate parameter is Object")
    void shouldAssignRawBeanTypeToObjectParameterizedDelegateType() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(
                DelegateAssignableService.class,
                RawDelegateAssignableBean.class,
                ObjectParameterizedDelegateDecorator.class
        );
        syringe.setup();

        @SuppressWarnings("rawtypes")
        DelegateAssignableService service = syringe.inject(DelegateAssignableService.class);
        assertEquals("decorator-object:raw:ok", service.echo("ok"));
    }

    @Test
    @DisplayName("20.3.1 - Parameterized bean type is assignable to wildcard-upper-bound delegate parameter")
    void shouldAssignParameterizedBeanTypeToUpperBoundWildcardDelegateParameter() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(
                DelegateAssignableService.class,
                StringDelegateAssignableBean.class,
                UpperWildcardDelegateDecorator.class,
                StringDelegateAssignableConsumer.class
        );
        syringe.setup();

        DelegateAssignableService<String> service = syringe.inject(StringDelegateAssignableConsumer.class).service;
        assertEquals("decorator-upper:string:ok", service.echo("ok"));
    }

    @Test
    @DisplayName("20.3.1 - Parameterized bean type is assignable to wildcard-lower-bound delegate parameter")
    void shouldAssignParameterizedBeanTypeToLowerBoundWildcardDelegateParameter() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(
                DelegateAssignableService.class,
                StringDelegateAssignableBean.class,
                LowerWildcardDelegateDecorator.class,
                StringDelegateAssignableConsumer.class
        );
        syringe.setup();

        DelegateAssignableService<String> service = syringe.inject(StringDelegateAssignableConsumer.class).service;
        assertEquals("decorator-lower:string:ok", service.echo("ok"));
    }

    @Test
    @DisplayName("20.3.1 - Parameterized bean type is assignable recursively for nested parameterized delegate parameters")
    void shouldAssignNestedParameterizedBeanTypeRecursively() {
        ResolutionRecorder.reset();
        Syringe syringe = newSyringe(
                DelegateAssignableService.class,
                ListStringDelegateAssignableBean.class,
                NestedWildcardDelegateDecorator.class,
                ListStringDelegateAssignableConsumer.class
        );
        syringe.setup();

        DelegateAssignableService<List<String>> service = syringe.inject(ListStringDelegateAssignableConsumer.class).service;
        assertEquals("decorator-nested:list:2", service.echo(Arrays.asList("a", "b")));
    }

    @Test
    @DisplayName("20.3.1 - Delegate type variable upper bound accepts compatible actual bean type")
    void shouldAssignActualBeanTypeToDelegateTypeVariableUpperBound() {
        SyntheticDecoratorRecorder.reset();
        Syringe syringe = newSyringe(
                TypeVariableDelegateDecoratorExtension.class,
                StringDelegateAssignableBean.class
        );
        syringe.addExtension(TypeVariableDelegateDecoratorExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = beanManager.resolveDecorators(
                Collections.<Type>singleton(new SimpleParameterizedType(
                        DelegateAssignableService.class,
                        new Type[]{String.class}
                )),
                Default.Literal.INSTANCE
        );
        assertTrue(
                decorators.stream().anyMatch(d -> d.getBeanClass().equals(TypeVariableCustomDecorator.class)),
                "Decorator with delegate type variable bound should match compatible actual bean type"
        );
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Collection<Class<?>> allFixtureTypes() {
        return Arrays.<Class<?>>asList(
                StringService.class,
                StringServiceBean.class,
                StringServiceDecorator.class,
                BeansXmlOnlyStringServiceDecorator.class,
                QualifiedService.class,
                Special.class,
                SpecialLiteral.class,
                SpecialQualifiedServiceBean.class,
                PlainQualifiedServiceBean.class,
                SpecialQualifiedDecorator.class,
                UnproxyableContract.class,
                FinalUnproxyableService.class,
                UnproxyableDecorator.class,
                SyntheticDecoratorExtension.class,
                PrioritizedSyntheticDecoratorExtension.class,
                TypeVariableDelegateDecoratorExtension.class,
                RecordingCustomDecorator.class,
                PrioritizedFastCustomDecorator.class,
                PrioritizedSlowCustomDecorator.class,
                DelegateAssignableService.class,
                RawDelegateAssignableBean.class,
                StringDelegateAssignableBean.class,
                ListStringDelegateAssignableBean.class,
                ObjectParameterizedDelegateDecorator.class,
                UpperWildcardDelegateDecorator.class,
                LowerWildcardDelegateDecorator.class,
                NestedWildcardDelegateDecorator.class,
                TypeVariableBoundDelegateDecorator.class,
                StringDelegateAssignableConsumer.class,
                ListStringDelegateAssignableConsumer.class,
                TypeVariableCustomDecorator.class
        );
    }

    private void addBeansXmlDecorators(Syringe syringe, String... decoratorClassNames) {
        StringBuilder classes = new StringBuilder();
        for (String decoratorClassName : decoratorClassNames) {
            classes.append("<class>").append(decoratorClassName).append("</class>");
        }
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<decorators>" + classes + "</decorators>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    static class ResolutionRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    public interface StringService {
        String value();
    }

    @Dependent
    public static class StringServiceBean implements StringService {
        @Override
        public String value() {
            ResolutionRecorder.record("business");
            return "value";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class StringServiceDecorator implements StringService {
        @Inject
        @Delegate
        StringService delegate;

        @Override
        public String value() {
            ResolutionRecorder.record("decorator-before");
            try {
                return "decorated:" + delegate.value();
            } finally {
                ResolutionRecorder.record("decorator-after");
            }
        }
    }

    @Decorator
    @Dependent
    public static class BeansXmlOnlyStringServiceDecorator implements StringService {
        @Inject
        @Delegate
        StringService delegate;

        @Override
        public String value() {
            ResolutionRecorder.record("beansxml-before");
            try {
                return "beansxml:" + delegate.value();
            } finally {
                ResolutionRecorder.record("beansxml-after");
            }
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Special {
    }

    public static final class SpecialLiteral extends AnnotationLiteral<Special> implements Special {
        static final SpecialLiteral INSTANCE = new SpecialLiteral();
    }

    public interface QualifiedService {
        String name();
    }

    @Dependent
    @Special
    public static class SpecialQualifiedServiceBean implements QualifiedService {
        @Override
        public String name() {
            ResolutionRecorder.record("special-business");
            return "special";
        }
    }

    @Dependent
    public static class PlainQualifiedServiceBean implements QualifiedService {
        @Override
        public String name() {
            ResolutionRecorder.record("plain-business");
            return "plain";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 20)
    public static class SpecialQualifiedDecorator implements QualifiedService {
        @Inject
        @Delegate
        @Special
        QualifiedService delegate;

        @Override
        public String name() {
            ResolutionRecorder.record("special-decorator-before");
            try {
                return "special-decorated:" + delegate.name();
            } finally {
                ResolutionRecorder.record("special-decorator-after");
            }
        }
    }

    public interface UnproxyableContract {
        String call();
    }

    @Dependent
    public static final class FinalUnproxyableService implements UnproxyableContract {
        @Override
        public String call() {
            return "final";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 30)
    public static class UnproxyableDecorator implements UnproxyableContract {
        @Inject
        @Delegate
        UnproxyableContract delegate;

        @Override
        public String call() {
            return delegate.call();
        }
    }

    static class SyntheticDecoratorRecorder {
        private static final java.util.Map<String, Integer> DELEGATE_TYPE_CALLS = new java.util.HashMap<String, Integer>();
        private static final java.util.Map<String, Integer> DELEGATE_QUALIFIER_CALLS = new java.util.HashMap<String, Integer>();
        private static final java.util.Map<String, Integer> DECORATED_TYPES_CALLS = new java.util.HashMap<String, Integer>();

        static synchronized void reset() {
            DELEGATE_TYPE_CALLS.clear();
            DELEGATE_QUALIFIER_CALLS.clear();
            DECORATED_TYPES_CALLS.clear();
        }

        static synchronized void recordDelegateType(String key) {
            Integer current = DELEGATE_TYPE_CALLS.get(key);
            DELEGATE_TYPE_CALLS.put(key, current == null ? 1 : current + 1);
        }

        static synchronized void recordDelegateQualifiers(String key) {
            Integer current = DELEGATE_QUALIFIER_CALLS.get(key);
            DELEGATE_QUALIFIER_CALLS.put(key, current == null ? 1 : current + 1);
        }

        static synchronized void recordDecoratedTypes(String key) {
            Integer current = DECORATED_TYPES_CALLS.get(key);
            DECORATED_TYPES_CALLS.put(key, current == null ? 1 : current + 1);
        }

        static synchronized int delegateTypeCalls(String key) {
            Integer value = DELEGATE_TYPE_CALLS.get(key);
            return value == null ? 0 : value;
        }

        static synchronized int delegateQualifierCalls(String key) {
            Integer value = DELEGATE_QUALIFIER_CALLS.get(key);
            return value == null ? 0 : value;
        }

        static synchronized int decoratedTypesCalls(String key) {
            Integer value = DECORATED_TYPES_CALLS.get(key);
            return value == null ? 0 : value;
        }
    }

    public static class SyntheticDecoratorExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addBean(new RecordingCustomDecorator());
        }
    }

    public static class PrioritizedSyntheticDecoratorExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addBean(new PrioritizedSlowCustomDecorator());
            afterBeanDiscovery.addBean(new PrioritizedFastCustomDecorator());
        }
    }

    public static class RecordingCustomDecorator implements jakarta.enterprise.inject.spi.Decorator<Object> {
        @Override
        public Type getDelegateType() {
            SyntheticDecoratorRecorder.recordDelegateType("recording");
            return StringService.class;
        }

        @Override
        public Set<Annotation> getDelegateQualifiers() {
            SyntheticDecoratorRecorder.recordDelegateQualifiers("recording");
            return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
        }

        @Override
        public Set<Type> getDecoratedTypes() {
            SyntheticDecoratorRecorder.recordDecoratedTypes("recording");
            return Collections.<Type>singleton(StringService.class);
        }

        @Override
        public Class<?> getBeanClass() {
            return RecordingCustomDecorator.class;
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
            return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
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
            return new HashSet<Type>(Arrays.<Type>asList(Object.class, StringService.class, jakarta.enterprise.inject.spi.Decorator.class));
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return this;
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }
    }

    public static class PrioritizedFastCustomDecorator extends RecordingCustomDecorator implements Prioritized {
        @Override
        public int getPriority() {
            return 50;
        }

        @Override
        public Class<?> getBeanClass() {
            return PrioritizedFastCustomDecorator.class;
        }
    }

    public static class PrioritizedSlowCustomDecorator extends RecordingCustomDecorator implements Prioritized {
        @Override
        public int getPriority() {
            return 500;
        }

        @Override
        public Class<?> getBeanClass() {
            return PrioritizedSlowCustomDecorator.class;
        }
    }

    public interface DelegateAssignableService<T> {
        String echo(T value);
    }

    @Dependent
    @SuppressWarnings("rawtypes")
    public static class RawDelegateAssignableBean implements DelegateAssignableService {
        @Override
        public String echo(Object value) {
            return "raw:" + value;
        }
    }

    @Dependent
    public static class StringDelegateAssignableBean implements DelegateAssignableService<String> {
        @Override
        public String echo(String value) {
            return "string:" + value;
        }
    }

    @Dependent
    public static class ListStringDelegateAssignableBean implements DelegateAssignableService<List<String>> {
        @Override
        public String echo(List<String> value) {
            return "list:" + value.size();
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 110)
    public static class ObjectParameterizedDelegateDecorator implements DelegateAssignableService<Object> {
        @Inject
        @Delegate
        DelegateAssignableService<Object> delegate;

        @Override
        public String echo(Object value) {
            return "decorator-object:" + delegate.echo(value);
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 120)
    public static class UpperWildcardDelegateDecorator implements DelegateAssignableService<String> {
        @Inject
        @Delegate
        DelegateAssignableService<? extends CharSequence> delegate;

        @Override
        public String echo(String value) {
            @SuppressWarnings("unchecked")
            DelegateAssignableService<String> target = (DelegateAssignableService<String>) delegate;
            return "decorator-upper:" + target.echo(value);
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 130)
    public static class LowerWildcardDelegateDecorator implements DelegateAssignableService<String> {
        @Inject
        @Delegate
        DelegateAssignableService<? super String> delegate;

        @Override
        public String echo(String value) {
            @SuppressWarnings("unchecked")
            DelegateAssignableService<String> target = (DelegateAssignableService<String>) delegate;
            return "decorator-lower:" + target.echo(value);
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 140)
    public static class NestedWildcardDelegateDecorator implements DelegateAssignableService<List<String>> {
        @Inject
        @Delegate
        DelegateAssignableService<List<? extends CharSequence>> delegate;

        @Override
        public String echo(List<String> value) {
            @SuppressWarnings("rawtypes")
            DelegateAssignableService target = (DelegateAssignableService) delegate;
            return "decorator-nested:" + target.echo(value);
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 150)
    public static class TypeVariableBoundDelegateDecorator<T extends CharSequence> implements DelegateAssignableService<String> {
        @Inject
        @Delegate
        DelegateAssignableService<T> delegate;

        @Override
        public String echo(String value) {
            @SuppressWarnings("unchecked")
            DelegateAssignableService<String> target = (DelegateAssignableService<String>) delegate;
            return "decorator-typevar:" + target.echo(value);
        }
    }

    @Dependent
    public static class StringDelegateAssignableConsumer {
        @Inject
        DelegateAssignableService<String> service;
    }

    @Dependent
    public static class ListStringDelegateAssignableConsumer {
        @Inject
        DelegateAssignableService<List<String>> service;
    }

    public static class TypeVariableDelegateDecoratorExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            afterBeanDiscovery.addBean(new TypeVariableCustomDecorator());
        }
    }

    public static class TypeVariableCustomDecorator<T extends CharSequence> implements jakarta.enterprise.inject.spi.Decorator<Object> {
        @Override
        public Type getDelegateType() {
            TypeVariable<?> typeVariable = TypeVariableCustomDecorator.class.getTypeParameters()[0];
            return new SimpleParameterizedType(
                    DelegateAssignableService.class,
                    new Type[]{typeVariable}
            );
        }

        @Override
        public Set<Annotation> getDelegateQualifiers() {
            return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
        }

        @Override
        public Set<Type> getDecoratedTypes() {
            return Collections.<Type>singleton(DelegateAssignableService.class);
        }

        @Override
        public Class<?> getBeanClass() {
            return TypeVariableCustomDecorator.class;
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
            return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
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
            return new HashSet<Type>(Arrays.<Type>asList(
                    Object.class,
                    DelegateAssignableService.class,
                    jakarta.enterprise.inject.spi.Decorator.class));
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public Object create(CreationalContext<Object> creationalContext) {
            return this;
        }

        @Override
        public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }
    }

    public static final class SimpleParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;

        public SimpleParameterizedType(Type rawType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments == null ? new Type[0] : actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
