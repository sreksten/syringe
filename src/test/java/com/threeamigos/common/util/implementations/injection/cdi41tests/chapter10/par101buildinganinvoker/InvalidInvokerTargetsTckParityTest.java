package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par101buildinganinvoker;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("10.1 - TCK parity for invalid invoker targets")
@Execution(ExecutionMode.SAME_THREAD)
class InvalidInvokerTargetsTckParityTest {

    @AfterEach
    void cleanup() {
        Recorder.reset();
    }

    @Test
    @DisplayName("MethodFromDifferentClassInvokerTest - method from a different class must fail deployment")
    void shouldFailForMethodFromDifferentClass() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DifferentClassService.class, DifferentClassOtherService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(MethodFromDifferentClassExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(Recorder.methodFromDifferentClassEnhancementInvoked);
        assertTrue(Recorder.methodFromDifferentClassRegistrationInvoked);
    }

    @Test
    @DisplayName("ObjectMethodButNotToStringInvokerTest - Object.hashCode must fail deployment")
    void shouldFailForObjectMethodButNotToString() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ObjectMethodService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(ObjectMethodButNotToStringExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(Recorder.objectMethodRegistrationInvoked);
    }

    @Test
    @DisplayName("PrivateMethodInvokerTest - private method target must fail deployment")
    void shouldFailForPrivateMethod() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrivateMethodService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(PrivateMethodExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(Recorder.privateMethodRegistrationInvoked);
    }

    @Test
    @DisplayName("ProducerFieldBeanInvokerTest - producer field bean must fail deployment")
    void shouldFailForProducerFieldBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerFieldOwner.class, ProducerFieldService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(ProducerFieldBeanExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(Recorder.producerFieldRegistrationInvoked);
    }

    @Test
    @DisplayName("ProducerMethodBeanInvokerTest - producer method bean must fail deployment")
    void shouldFailForProducerMethodBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerMethodOwner.class, ProducerMethodService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(ProducerMethodBeanExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(Recorder.producerMethodRegistrationInvoked);
    }

    @ApplicationScoped
    public static class DifferentClassService {
    }

    @ApplicationScoped
    public static class DifferentClassOtherService {
        public void doSomething() {
        }
    }

    public static class MethodFromDifferentClassExtension implements BuildCompatibleExtension {
        private MethodInfo doSomething;

        @Enhancement(types = DifferentClassOtherService.class)
        public void myOtherServiceEnhancement(MethodInfo method) {
            Recorder.methodFromDifferentClassEnhancementInvoked = true;
            if ("doSomething".equals(method.name())) {
                doSomething = method;
            }
        }

        @Registration(types = DifferentClassService.class)
        public void myServiceRegistration(BeanInfo bean, InvokerFactory invokers) {
            Recorder.methodFromDifferentClassRegistrationInvoked = true;
            invokers.createInvoker(bean, doSomething).build();
        }
    }

    @ApplicationScoped
    public static class ObjectMethodService {
    }

    public static class ObjectMethodButNotToStringExtension implements BuildCompatibleExtension {
        @Registration(types = ObjectMethodService.class)
        public void myServiceRegistration(BeanInfo bean, InvokerFactory invokers) {
            Recorder.objectMethodRegistrationInvoked = true;
            for (MethodInfo method : bean.declaringClass().superClassDeclaration().methods()) {
                if ("hashCode".equals(method.name())) {
                    invokers.createInvoker(bean, method).build();
                }
            }
        }
    }

    @ApplicationScoped
    public static class PrivateMethodService {
        private String hello() {
            return "foobar";
        }
    }

    public static class PrivateMethodExtension implements BuildCompatibleExtension {
        @Registration(types = PrivateMethodService.class)
        public void myServiceRegistration(BeanInfo bean, InvokerFactory invokers) {
            Recorder.privateMethodRegistrationInvoked = true;
            bean.declaringClass()
                .methods()
                .stream()
                .filter(it -> "hello".equals(it.name()))
                .forEach(it -> invokers.createInvoker(bean, it).build());
        }
    }

    @ApplicationScoped
    public static class ProducerFieldOwner {
        @Produces
        public static ProducerFieldService producer = new ProducerFieldService();
    }

    public static class ProducerFieldService {
        public String hello() {
            return "foobar";
        }
    }

    public static class ProducerFieldBeanExtension implements BuildCompatibleExtension {
        @Registration(types = ProducerFieldService.class)
        public void myServiceRegistration(BeanInfo bean, InvokerFactory invokers) {
            Recorder.producerFieldRegistrationInvoked = true;
            bean.producerField()
                .type()
                .asClass()
                .declaration()
                .methods()
                .stream()
                .filter(it -> "hello".equals(it.name()))
                .forEach(it -> invokers.createInvoker(bean, it).build());
        }
    }

    @ApplicationScoped
    public static class ProducerMethodOwner {
        @Produces
        public static ProducerMethodService produce() {
            return new ProducerMethodService();
        }
    }

    public static class ProducerMethodService {
        public String hello() {
            return "foobar";
        }
    }

    public static class ProducerMethodBeanExtension implements BuildCompatibleExtension {
        @Registration(types = ProducerMethodService.class)
        public void myServiceRegistration(BeanInfo bean, InvokerFactory invokers) {
            Recorder.producerMethodRegistrationInvoked = true;
            bean.producerMethod()
                .returnType()
                .asClass()
                .declaration()
                .methods()
                .stream()
                .filter(it -> "hello".equals(it.name()))
                .forEach(it -> invokers.createInvoker(bean, it).build());
        }
    }

    public static class Recorder {
        static volatile boolean methodFromDifferentClassEnhancementInvoked;
        static volatile boolean methodFromDifferentClassRegistrationInvoked;
        static volatile boolean objectMethodRegistrationInvoked;
        static volatile boolean privateMethodRegistrationInvoked;
        static volatile boolean producerFieldRegistrationInvoked;
        static volatile boolean producerMethodRegistrationInvoked;

        static void reset() {
            methodFromDifferentClassEnhancementInvoked = false;
            methodFromDifferentClassRegistrationInvoked = false;
            objectMethodRegistrationInvoked = false;
            privateMethodRegistrationInvoked = false;
            producerFieldRegistrationInvoked = false;
            producerMethodRegistrationInvoked = false;
        }
    }
}
