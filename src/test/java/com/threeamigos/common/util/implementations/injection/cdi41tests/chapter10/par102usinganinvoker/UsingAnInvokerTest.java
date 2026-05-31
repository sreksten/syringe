package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par102usinganinvoker;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.wrappers.AnnotatedMethodWrapper;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.invoke.Invoker;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("10.2 - Using an Invoker")
@Execution(ExecutionMode.SAME_THREAD)
public class UsingAnInvokerTest {

    @Test
    @DisplayName("10.2 - invoke() returns target method return value and supports repeated invocations with different instances and arguments")
    void shouldInvokeMultipleTimesWithDifferentInstancesAndArguments() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertEquals("a1", invoker.invoke(new BasicTargetBean(), "a", 1));
        assertEquals("b2", invoker.invoke(new BasicTargetBean(), "b", 2));
    }

    @Test
    @DisplayName("10.2.1 - For static target method the instance is ignored")
    void shouldIgnoreInstanceForStaticMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("staticEcho", String.class));

        assertEquals("x", invoker.invoke(null, "x"));
        assertEquals("y", invoker.invoke(new BasicTargetBean(), "y"));
    }

    @Test
    @DisplayName("10.2.1 - For non-static target method null instance causes RuntimeException")
    void shouldRejectNullInstanceForNonStaticMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertThrows(RuntimeException.class, () -> invoker.invoke(null, "a", 1));
    }

    @Test
    @DisplayName("10.2.1 - For non-static target method non-permissible instance causes RuntimeException")
    void shouldRejectNonPermissibleInstance() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertThrows(RuntimeException.class, () -> invoker.invoke(new UnrelatedInstance(), "a", 1));
    }

    @Test
    @DisplayName("10.2.1 - If target method has no parameters arguments are ignored")
    void shouldIgnoreArgumentsForNoArgMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("noArgs"));

        assertEquals("no-args", invoker.invoke(new BasicTargetBean(), "extra", 123, new Object()));
    }

    @Test
    @DisplayName("10.2.1 - If target method declares parameters and arguments is null RuntimeException is thrown")
    void shouldRejectNullArgumentsForParameterizedMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertThrows(RuntimeException.class, () -> invoker.invoke(new BasicTargetBean(), (Object[]) null));
    }

    @Test
    @DisplayName("10.2.1 - If fewer arguments than target parameters are provided RuntimeException is thrown")
    void shouldRejectFewerArgumentsThanParameters() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertThrows(RuntimeException.class, () -> invoker.invoke(new BasicTargetBean(), "only-one"));
    }

    @Test
    @DisplayName("10.2.1 - If more arguments than target parameters are provided excess arguments are ignored")
    void shouldIgnoreExcessArguments() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertEquals("c3", invoker.invoke(new BasicTargetBean(), "c", 3, "ignored", 999));
    }

    @Test
    @DisplayName("10.2.1 - If method invocation conversion does not exist for an argument RuntimeException is thrown")
    void shouldRejectInvalidArgumentConversion() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        assertThrows(RuntimeException.class, () -> invoker.invoke(new BasicTargetBean(), "x", "not-an-integer"));
    }

    @Test
    @DisplayName("10.2.1 - Variable arity target method expects the last argument to be an array")
    void shouldInvokeVarArgsMethodWithLastArgumentArray() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("joinVarargs", String[].class));

        assertEquals("a,b,c", invoker.invoke(new BasicTargetBean(), (Object) new String[]{"a", "b", "c"}));
        assertThrows(RuntimeException.class, () -> invoker.invoke(new BasicTargetBean(), "a", "b"));
    }

    @Test
    @DisplayName("10.2.1 - If target method returns void invoke() returns null and primitive return types are boxed")
    void shouldReturnNullForVoidAndBoxPrimitiveReturn() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker voidInvoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("mark"));
        Invoker primitiveInvoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("primitiveValue"));

        assertNull(voidInvoker.invoke(new BasicTargetBean()));
        Object boxed = primitiveInvoker.invoke(new BasicTargetBean());
        assertTrue(boxed instanceof Integer);
        assertEquals(7, boxed);
    }

    @Test
    @DisplayName("10.2.1 - If target method throws an exception it is rethrown directly")
    void shouldRethrowTargetExceptionDirectly() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("throwChecked"));

        Exception ex = assertThrows(Exception.class, () -> invoker.invoke(new BasicTargetBean()));
        assertEquals("checked", ex.getMessage());
    }

    @Test
    @DisplayName("10.2 - Invoker implementation is thread-safe for concurrent use")
    void shouldBeThreadSafeForConcurrentInvocations() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(BasicTargetBean.class);
        syringe.setup();

        final Invoker invoker = createInvoker(CapturedManagedBeans.get(BasicTargetBean.class),
                BasicTargetBean.class.getDeclaredMethod("concat", String.class, Integer.class));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Set<Callable<String>> tasks = new HashSet<Callable<String>>();
            for (int i = 0; i < 20; i++) {
                final int idx = i;
                tasks.add(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return (String) invoker.invoke(new BasicTargetBean(), "t", idx);
                    }
                });
            }
            for (Future<String> future : pool.invokeAll(tasks)) {
                assertTrue(future.get().startsWith("t"));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("10.2 - Invoker-based indirect invocation is a business method invocation when direct call is business invocation")
    void shouldInvokeBusinessMethodThroughInterceptorWhenUsingInvoker() throws Exception {
        InterceptorRecorder.reset();
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(InterceptedBean.class, TracedInterceptor.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(InterceptedBean.class),
                InterceptedBean.class.getDeclaredMethod("ping"));
        InterceptedBean bean = getBeanInstance(syringe, InterceptedBean.class);

        assertEquals("pong", invoker.invoke(bean));
        assertEquals(1, InterceptorRecorder.aroundInvokeCalls);
    }

    @Test
    @DisplayName("10.2.2 - Example invocation through invoker on @Dependent bean returns expected greeting")
    void shouldMatchSpecificationExampleInvocation() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(MyService.class);
        syringe.setup();

        Invoker invoker = createInvoker(CapturedManagedBeans.get(MyService.class),
                MyService.class.getDeclaredMethod("hello", String.class));
        MyService myService = getBeanInstance(syringe, MyService.class);

        assertEquals("Hello world!", invoker.invoke(myService, new Object[]{"world"}));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.addExtension(CaptureManagedBeanExtension.class.getName());
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        Class<?>[] allFixtures = new Class<?>[]{
                BasicTargetBean.class,
                InterceptedBean.class,
                TracedInterceptor.class,
                MyService.class
        };
        for (Class<?> fixture : allFixtures) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Invoker createInvoker(ProcessManagedBean<?> pmb, Method javaMethod) {
        AnnotatedMethodWrapper wrapper = new AnnotatedMethodWrapper(javaMethod, pmb.getAnnotatedBeanClass());
        return (Invoker) pmb.createInvoker(wrapper).build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getBeanInstance(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beans);
        return (T) beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
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
                Collections.synchronizedMap(new HashMap<Class<?>, ProcessManagedBean<?>>());

        static void reset() {
            BEANS.clear();
        }

        static void put(Class<?> beanClass, ProcessManagedBean<?> pmb) {
            BEANS.put(beanClass, pmb);
        }

        static ProcessManagedBean<?> get(Class<?> beanClass) {
            return BEANS.get(beanClass);
        }
    }

    @ApplicationScoped
    public static class BasicTargetBean {
        public String concat(String prefix, Integer value) {
            return prefix + value;
        }

        public static String staticEcho(String value) {
            return value;
        }

        public String noArgs() {
            return "no-args";
        }

        public String joinVarargs(String... values) {
            return String.join(",", values);
        }

        public void mark() {
        }

        public int primitiveValue() {
            return 7;
        }

        public String throwChecked() throws Exception {
            throw new Exception("checked");
        }
    }

    public static class UnrelatedInstance {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Traced {
    }

    @Traced
    @Interceptor
    @Priority(1000)
    public static class TracedInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            InterceptorRecorder.aroundInvokeCalls++;
            return ctx.proceed();
        }
    }

    @ApplicationScoped
    @Traced
    public static class InterceptedBean {
        public String ping() {
            return "pong";
        }
    }

    @Dependent
    public static class MyService {
        public String hello(String name) {
            return "Hello " + name + "!";
        }
    }

    public static class InterceptorRecorder {
        static int aroundInvokeCalls;

        static void reset() {
            aroundInvokeCalls = 0;
        }
    }
}
