package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par102usinganinvoker;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.wrappers.AnnotatedMethodWrapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.invoke.Invoker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("10.2.1 - Varargs method invoker regression")
class VarargsMethodInvokerRegressionTest {

    @Test
    @DisplayName("10.2.1 - Invoker accepts varargs array and rejects non-array varargs argument")
    void shouldInvokeVarargsMethodsAndRejectNonArrayVarargsArgument() throws Exception {
        CapturedManagedBeans.reset();

        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(CaptureManagedBeanExtension.class.getName());
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(MyService.class, BeanArchiveMode.EXPLICIT);
            syringe.start();

            ProcessManagedBean<?> pmb = CapturedManagedBeans.get(MyService.class);
            Invoker hello = createInvoker(pmb, MyService.class.getDeclaredMethod("hello", int.class, String[].class));
            Invoker helloStatic = createInvoker(
                    pmb,
                    MyService.class.getDeclaredMethod("helloStatic", int.class, String[].class)
            );

            assertEquals("foobar0[]", hello.invoke(new MyService(), new Object[]{0, new String[]{}}));
            assertEquals("foobar1[a]", hello.invoke(new MyService(), new Object[]{1, new String[]{"a"}}));
            assertThrows(RuntimeException.class, () -> hello.invoke(null, new Object[]{1, "a"}));

            assertEquals("quux0[b]", helloStatic.invoke(null, new Object[]{0, new String[]{"b"}}));
            assertEquals("quux1[c]", helloStatic.invoke(null, new Object[]{1, new String[]{"c"}}));
            assertThrows(RuntimeException.class, () -> helloStatic.invoke(null, new Object[]{1, "a"}));
        } finally {
            syringe.shutdown();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Invoker createInvoker(ProcessManagedBean<?> pmb, Method method) {
        AnnotatedMethodWrapper wrapper = new AnnotatedMethodWrapper(method, pmb.getAnnotatedBeanClass());
        return (Invoker) pmb.createInvoker(wrapper).build();
    }

    public static class CaptureManagedBeanExtension implements Extension {
        public void capture(@Observes ProcessBean<?> processBean) {
            if (processBean instanceof ProcessManagedBean) {
                ProcessManagedBean<?> pmb = (ProcessManagedBean<?>) processBean;
                CapturedManagedBeans.put(pmb.getBean().getBeanClass(), pmb);
            }
        }
    }

    public static class CapturedManagedBeans {
        private static final Map<Class<?>, ProcessManagedBean<?>> BEANS =
                new ConcurrentHashMap<Class<?>, ProcessManagedBean<?>>();

        static void put(Class<?> beanClass, ProcessManagedBean<?> processManagedBean) {
            BEANS.put(beanClass, processManagedBean);
        }

        static ProcessManagedBean<?> get(Class<?> beanClass) {
            return BEANS.get(beanClass);
        }

        static void reset() {
            BEANS.clear();
        }
    }

    @ApplicationScoped
    public static class MyService {
        public String hello(int param1, String... param2) {
            return "foobar" + param1 + Arrays.toString(param2);
        }

        public static String helloStatic(int param1, String... param2) {
            return "quux" + param1 + Arrays.toString(param2);
        }
    }
}
