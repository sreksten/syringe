package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter19.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Chapter 19 - Interceptors TCK parity")
class InterceptorsTckParityTest {
    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            LakeCargoShip.class, CargoShip.class, Ship.class, LifecycleInterceptor1.class, LifecycleInterceptor2.class,
            LifecycleInterceptor3.class, LifecycleInterceptor4.class, AccountHolder.class, TransactionInterceptor.class,
            SuperInterceptor1.class, MiddleInterceptor1.class, Interceptor1.class, SuperInterceptor2.class, Interceptor2.class,
            SuperFoo.class, MiddleFoo.class, Foo.class, ConstructorTargetFoo.class, ConstructorTargetFooInterceptor.class,
            Fighter.class, Spitfire.class, LandingInterceptor.class
    };

    @Test
    @DisplayName("19.2 / PostConstructOrderTest - lifecycle callback interceptor ordering follows expected chain")
    void shouldMatchPostConstructOrderTestInvocationOrder() {
        LifecycleOrderRecorder.reset();

        Syringe syringe = newSyringe(
                LakeCargoShip.class,
                LifecycleInterceptor1.class,
                LifecycleInterceptor4.class
        );
        syringe.setup();

        syringe.inject(LakeCargoShip.class);
        assertEquals(Arrays.asList("Interceptor1", "Interceptor2", "Interceptor3", "Interceptor4", "Ship", "CargoShip", "LakeCargoShip"),
                LifecycleOrderRecorder.events());
    }

    @Test
    @DisplayName("19.2 / InterceptorNotListedInBeansXmlNotEnabledTest - interceptor not listed in beans.xml is not invoked")
    void shouldMatchInterceptorNotListedInBeansXmlNotEnabledTest() {
        TransactionInterceptor.invoked = false;

        Syringe syringe = newSyringe(AccountHolder.class, TransactionInterceptor.class);
        addBeansXmlInterceptors(syringe);
        syringe.setup();

        AccountHolder accountHolder = syringe.inject(AccountHolder.class);
        accountHolder.transfer(0.0);

        assertFalse(TransactionInterceptor.invoked);
    }

    @Test
    @DisplayName("19.0 / AroundInvokeInterceptorTest - around-invoke interception order includes interceptor and target inheritance")
    void shouldMatchAroundInvokeInterceptorTestBusinessMethodOrder() {
        InvocationRecorder.reset();

        Syringe syringe = newSyringe(Foo.class, Interceptor1.class, Interceptor2.class);
        syringe.setup();

        Foo foo = syringe.inject(Foo.class);
        foo.ping();

        assertEquals(
                Arrays.asList(
                        "SuperInterceptor1",
                        "MiddleInterceptor1",
                        "Interceptor1",
                        "SuperInterceptor2",
                        "Interceptor2",
                        "SuperFoo",
                        "MiddleFoo",
                        "Foo"
                ),
                InvocationRecorder.events()
        );
    }

    @Test
    @DisplayName("19.0 / AroundConstructInterceptorReturnValueTest - @AroundConstruct return value is ignored")
    void shouldMatchAroundConstructInterceptorReturnValueIgnoredTest() {
        Syringe syringe = newSyringe(ConstructorTargetFoo.class, ConstructorTargetFooInterceptor.class);
        syringe.setup();

        ConstructorTargetFoo foo = syringe.inject(ConstructorTargetFoo.class);
        assertEquals("default", foo.getName());
    }

    @Test
    @DisplayName("19.1 / FinalMethodWithInheritedStereotypeInterceptorTest - final overridden method with inherited stereotype binding is deployment problem")
    void shouldMatchFinalMethodWithInheritedStereotypeInterceptorTest() {
        Syringe syringe = newSyringe(Fighter.class, FighterStereotype.class, Spitfire.class, LandingBinding.class,
                LandingInterceptor.class);

        assertThrowsAny(syringe);
    }

    private void assertThrowsAny(Syringe syringe) {
        try {
            syringe.setup();
        } catch (DeploymentException | DefinitionException expected) {
            return;
        }
        throw new AssertionError("Expected deployment problem to be thrown");
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(classes));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private void addBeansXmlInterceptors(Syringe syringe, String... interceptorClassNames) {
        StringBuilder classes = new StringBuilder();
        for (String interceptorClassName : interceptorClassNames) {
            classes.append("<class>").append(interceptorClassName).append("</class>");
        }

        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\" bean-discovery-mode=\"all\">" +
                "<interceptors>" + classes + "</interceptors>" +
                "</beans>";

        BeansXml beansXml = new BeansXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    static final class LifecycleOrderRecorder {
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

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @InterceptorBinding
    public @interface LakeCargoShipClassBinding {
    }

    @Interceptor
    @LakeCargoShipClassBinding
    @Priority(1)
    public static class LifecycleInterceptor1 {
        @PostConstruct
        void postConstruct1(InvocationContext ctx) {
            LifecycleOrderRecorder.record("Interceptor1");
            try {
                ctx.proceed();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class LifecycleInterceptor2 {
        @PostConstruct
        void postConstruct2(InvocationContext ctx) {
            LifecycleOrderRecorder.record("Interceptor2");
            try {
                ctx.proceed();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class LifecycleInterceptor3 extends LifecycleInterceptor2 {
        @PostConstruct
        void postConstruct3(InvocationContext ctx) {
            LifecycleOrderRecorder.record("Interceptor3");
            try {
                ctx.proceed();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Interceptor
    @LakeCargoShipClassBinding
    @Priority(4)
    public static class LifecycleInterceptor4 extends LifecycleInterceptor3 {
        @PostConstruct
        void postConstruct(InvocationContext ctx) {
            LifecycleOrderRecorder.record("Interceptor4");
            try {
                ctx.proceed();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class Ship {
        @PostConstruct
        void postConstruct1() {
            LifecycleOrderRecorder.record("Ship");
        }
    }

    static class CargoShip extends Ship {
        @PostConstruct
        void postConstruct2() {
            LifecycleOrderRecorder.record("CargoShip");
        }
    }

    @LakeCargoShipClassBinding
    @Dependent
    public static class LakeCargoShip extends CargoShip {
        @PostConstruct
        void postConstruct3() {
            LifecycleOrderRecorder.record("LakeCargoShip");
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @InterceptorBinding
    public @interface Transactional {
    }

    @Transactional
    @Interceptor
    public static class TransactionInterceptor {
        static boolean invoked;

        @AroundInvoke
        public Object alwaysReturnThis(InvocationContext ctx) throws Exception {
            invoked = true;
            return ctx.proceed();
        }
    }

    public static class AccountHolder {
        @Transactional
        public void transfer(double amount) {
        }
    }

    static final class InvocationRecorder {
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

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @InterceptorBinding
    public @interface Binding {
    }

    public static class SuperInterceptor1 {
        @AroundInvoke
        public Object intercept0(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("SuperInterceptor1");
            return ctx.proceed();
        }
    }

    public static class MiddleInterceptor1 extends SuperInterceptor1 {
        @AroundInvoke
        public Object intercept1(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("MiddleInterceptor1");
            return ctx.proceed();
        }
    }

    @Interceptor
    @Priority(1001)
    @Binding
    public static class Interceptor1 extends MiddleInterceptor1 {
        @AroundInvoke
        public Object intercept2(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("Interceptor1");
            return ctx.proceed();
        }
    }

    public static class SuperInterceptor2 {
        @AroundInvoke
        public Object intercept0(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("SuperInterceptor2");
            return ctx.proceed();
        }
    }

    @Interceptor
    @Priority(1002)
    @Binding
    public static class Interceptor2 extends SuperInterceptor2 {
        @AroundInvoke
        public Object intercept1(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("Interceptor2");
            return ctx.proceed();
        }
    }

    public static class SuperFoo {
        @AroundInvoke
        public Object intercept0(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("SuperFoo");
            return ctx.proceed();
        }
    }

    public static class MiddleFoo extends SuperFoo {
        @AroundInvoke
        public Object intercept1(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("MiddleFoo");
            return ctx.proceed();
        }
    }

    @Binding
    @Dependent
    public static class Foo extends MiddleFoo {
        public void ping() {
        }

        @AroundInvoke
        public Object intercept2(InvocationContext ctx) throws Exception {
            InvocationRecorder.record("Foo");
            return ctx.proceed();
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Inherited
    @InterceptorBinding
    public @interface FooBinding {
    }

    @FooBinding
    @Dependent
    public static class ConstructorTargetFoo {
        private String name;

        public ConstructorTargetFoo() {
            this.name = "default";
        }

        public ConstructorTargetFoo(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    @FooBinding
    @Interceptor
    @Priority(2600)
    public static class ConstructorTargetFooInterceptor {
        @AroundConstruct
        public Object aroundConstruct(InvocationContext ctx) throws Exception {
            ctx.proceed();
            return new ConstructorTargetFoo("intercepted");
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Inherited
    @InterceptorBinding
    public @interface LandingBinding {
    }

    @LandingBinding
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION)
    public static class LandingInterceptor {
        @AroundInvoke
        public Object alwaysReturnThis(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @LandingBinding
    @Inherited
    @Stereotype
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FighterStereotype {
    }

    @FighterStereotype
    @Dependent
    public static class Fighter {
        public void fire() {
        }
    }

    @Dependent
    public static class Spitfire extends Fighter {
        public final void fire() {
        }
    }
}
