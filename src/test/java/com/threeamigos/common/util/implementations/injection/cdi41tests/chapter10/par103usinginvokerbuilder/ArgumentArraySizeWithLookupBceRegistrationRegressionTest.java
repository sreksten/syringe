package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par103usinginvokerbuilder;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.invoke.Invoker;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("10.3.1 - Argument array size with lookup regression")
@Tag("bce-conformance")
@Execution(ExecutionMode.SAME_THREAD)
class ArgumentArraySizeWithLookupBceRegistrationRegressionTest {

    @Test
    @DisplayName("10.3.1 - BCE registration can build invoker with argument lookup before AfterBeanDiscovery")
    void shouldMatchArgumentArraySizeSemanticsWhenInvokerIsBuiltInRegistrationPhase() throws Exception {
        RegistrationPhaseLookupExtension.reset();
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(RegistrationPhaseLookupExtension.class.getName());

        try {
            syringe.initialize();
            syringe.addDiscoveredClass(MyService.class, BeanArchiveMode.EXPLICIT);
            syringe.addDiscoveredClass(MyDependency1.class, BeanArchiveMode.EXPLICIT);
            syringe.addDiscoveredClass(MyDependency2.class, BeanArchiveMode.EXPLICIT);
            syringe.start();

            InvokerHolder holder = syringe.inject(InvokerHolder.class);
            MyService service = syringe.inject(MyService.class);
            Invoker<MyService, String> invoker = holder.hello();

            assertThrows(RuntimeException.class, () -> invoker.invoke(service, (Object[]) null));
            assertThrows(RuntimeException.class, () -> invoker.invoke(service, new Object[]{}));
            assertThrows(RuntimeException.class, () -> invoker.invoke(service, new Object[]{null}));

            assertEquals("foobar_1_2", invoker.invoke(service, new Object[]{null, null}));
            assertEquals("foobar_1_2", invoker.invoke(service, new Object[]{null, null, null}));
        } finally {
            syringe.shutdown();
            RegistrationPhaseLookupExtension.reset();
        }
    }

    public static class RegistrationPhaseLookupExtension implements BuildCompatibleExtension {
        static volatile InvokerInfo helloInvoker;

        static void reset() {
            helloInvoker = null;
        }

        @Registration(types = MyService.class)
        public void registration(BeanInfo bean, InvokerFactory invokers) {
            MethodInfo helloMethod = bean.declaringClass().methods().stream()
                .filter(method -> "hello".equals(method.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Method 'hello' was not found on " + bean.declaringClass()));

            helloInvoker = invokers.createInvoker(bean, helloMethod)
                .withArgumentLookup(0)
                .withArgumentLookup(1)
                .build();
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            syntheticComponents.addBean(InvokerHolder.class)
                .withParam("hello", helloInvoker)
                .createWith(InvokerHolderCreator.class);
        }
    }

    public static class InvokerHolderCreator implements SyntheticBeanCreator<InvokerHolder> {
        @SuppressWarnings("unchecked")
        @Override
        public InvokerHolder create(Instance<Object> lookup, Parameters params) {
            return new InvokerHolder((Invoker<MyService, String>) params.get("hello", Invoker.class));
        }
    }

    public static class InvokerHolder {
        private final Invoker<MyService, String> hello;

        InvokerHolder(Invoker<MyService, String> hello) {
            this.hello = hello;
        }

        Invoker<MyService, String> hello() {
            return hello;
        }
    }

    @ApplicationScoped
    public static class MyService {
        public String hello(MyDependency1 dependency1, MyDependency2 dependency2) {
            return "foobar_" + dependency1 + "_" + dependency2;
        }
    }

    @Dependent
    public static class MyDependency1 {
        @Override
        public String toString() {
            return "1";
        }
    }

    @Dependent
    public static class MyDependency2 {
        @Override
        public String toString() {
            return "2";
        }
    }
}
