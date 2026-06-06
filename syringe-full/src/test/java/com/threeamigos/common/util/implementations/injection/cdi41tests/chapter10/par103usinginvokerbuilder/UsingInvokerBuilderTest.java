package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par103usinginvokerbuilder;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.spi.wrappers.AnnotatedMethodWrapper;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserverBuilder;
import jakarta.enterprise.invoke.InvokerBuilder;
import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Qualifier;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("10.3 - Using Invoker Builder")
@Tag("bce-conformance")
@Execution(ExecutionMode.SAME_THREAD)
public class UsingInvokerBuilderTest {

    @Test
    @DisplayName("10.3 - InvokerBuilder is obtained from InvokerFactory.createInvoker(BeanInfo, MethodInfo)")
    public void shouldExposeInvokerFactoryCreateInvokerContract() throws Exception {
        Method method = InvokerFactory.class.getMethod("createInvoker", BeanInfo.class, MethodInfo.class);
        assertNotNull(method);
        assertEquals(InvokerBuilder.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
        assertEquals(BeanInfo.class, method.getParameterTypes()[0]);
        assertEquals(MethodInfo.class, method.getParameterTypes()[1]);
    }

    @Test
    @DisplayName("10.3 - InvokerFactory.createInvoker returns InvokerBuilder parameterized with InvokerInfo token")
    public void shouldReturnInvokerBuilderOfInvokerInfo() throws Exception {
        Method method = InvokerFactory.class.getMethod("createInvoker", BeanInfo.class, MethodInfo.class);
        Type genericReturn = method.getGenericReturnType();
        assertTrue(genericReturn instanceof ParameterizedType);

        ParameterizedType pt = (ParameterizedType) genericReturn;
        assertEquals(InvokerBuilder.class, pt.getRawType());
        assertEquals(InvokerInfo.class, pt.getActualTypeArguments()[0]);
    }

    @Test
    @DisplayName("10.3 - InvokerFactory may be declared as parameter of @Registration build compatible extension method")
    public void shouldAllowInvokerFactoryParameterOnRegistrationMethod() throws Exception {
        Method registration = ExampleBuildCompatibleExtension.class.getDeclaredMethod("registration", InvokerFactory.class);

        assertTrue(AnnotationPredicates.hasRegistrationAnnotation(registration));
        assertEquals(1, registration.getParameterCount());
        assertEquals(InvokerFactory.class, registration.getParameterTypes()[0]);
    }

    @Test
    @DisplayName("10.3 - InvokerBuilder.build produces opaque token type that can be carried as InvokerInfo")
    public void shouldExposeInvokerBuilderBuildTokenType() throws Exception {
        Method build = InvokerBuilder.class.getMethod("build");
        assertEquals(Object.class, build.getReturnType());

        Method createInvoker = InvokerFactory.class.getMethod("createInvoker", BeanInfo.class, MethodInfo.class);
        ParameterizedType returnedBuilderType = (ParameterizedType) createInvoker.getGenericReturnType();
        assertEquals(InvokerInfo.class, returnedBuilderType.getActualTypeArguments()[0]);
    }

    @Test
    @DisplayName("10.3 - InvokerInfo token can be passed to SyntheticBeanBuilder and SyntheticObserverBuilder")
    public void shouldAllowPassingInvokerInfoToSyntheticBuilders() throws Exception {
        Method beanWithParam = SyntheticBeanBuilder.class.getMethod("withParam", String.class, InvokerInfo.class);
        Method observerWithParam = SyntheticObserverBuilder.class.getMethod("withParam", String.class, InvokerInfo.class);

        assertNotNull(beanWithParam);
        assertNotNull(observerWithParam);
        assertEquals(SyntheticBeanBuilder.class, beanWithParam.getReturnType());
        assertEquals(SyntheticObserverBuilder.class, observerWithParam.getReturnType());
    }

    @Test
    @DisplayName("10.3.1 - InvokerBuilder exposes withInstanceLookup() and withArgumentLookup(int) methods")
    public void shouldExposeLookupConfigurationMethods() throws Exception {
        Method withInstanceLookup = InvokerBuilder.class.getMethod("withInstanceLookup");
        Method withArgumentLookup = InvokerBuilder.class.getMethod("withArgumentLookup", int.class);
        assertEquals(InvokerBuilder.class, withInstanceLookup.getReturnType());
        assertEquals(InvokerBuilder.class, withArgumentLookup.getReturnType());
    }

    @Test
    @DisplayName("10.3.1 - withArgumentLookup rejects positions below 0 and beyond target parameter count")
    public void shouldRejectOutOfRangeArgumentLookupPositions() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(LookupTargetBean.class);
        syringe.setup();

        InvokerBuilder<Invoker<LookupTargetBean, ?>> builder =
                createInvokerBuilder(CapturedManagedBeans.get(LookupTargetBean.class),
                        LookupTargetBean.class.getDeclaredMethod("concat", String.class, String.class));

        assertThrows(IllegalArgumentException.class, () -> builder.withArgumentLookup(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.withArgumentLookup(2));
    }

    @Test
    @DisplayName("10.3.1 - withInstanceLookup ignores instance for non-static methods and uses contextual bean instance")
    public void shouldUseContextualInstanceWhenInstanceLookupConfigured() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(LookupTargetBean.class);
        syringe.setup();

        LookupTargetBean contextual = getBeanInstance(syringe, LookupTargetBean.class);
        LookupTargetBean manual = new LookupTargetBean();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(LookupTargetBean.class),
                LookupTargetBean.class.getDeclaredMethod("concat", String.class, String.class))
                .withInstanceLookup()
                .build();

        String result = (String) invoker.invoke(manual, "A", "B");
        assertTrue(result.startsWith(contextual.id + ":"));
        assertTrue(!result.startsWith(manual.id + ":"));
    }

    @Test
    @DisplayName("10.3.1 - withInstanceLookup has no effect for static target method")
    public void shouldHaveNoEffectForStaticMethodInstanceLookup() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(LookupTargetBean.class);
        syringe.setup();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(LookupTargetBean.class),
                LookupTargetBean.class.getDeclaredMethod("staticEcho", String.class))
                .withInstanceLookup()
                .build();

        assertEquals("ok", invoker.invoke(null, "ok"));
    }

    @Test
    @DisplayName("10.3.1 - withArgumentLookup resolves bean by parameter type and qualifiers and ignores provided argument")
    public void shouldResolveLookedUpArgumentByTypeAndQualifier() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(ArgumentLookupBean.class, BlueService.class, RedService.class);
        syringe.setup();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(ArgumentLookupBean.class),
                ArgumentLookupBean.class.getDeclaredMethod("use", Service.class, String.class))
                .withArgumentLookup(0)
                .build();

        String result = (String) invoker.invoke(new ArgumentLookupBean(), new RedService(), "x");
        assertEquals("blue:x", result);
    }

    @Test
    @DisplayName("10.3.1 - Lookup configuration does not relax invoke argument-length requirements")
    public void shouldStillRequireAllArgumentsEvenWithLookupConfigured() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(ArgumentLookupBean.class, BlueService.class);
        syringe.setup();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(ArgumentLookupBean.class),
                ArgumentLookupBean.class.getDeclaredMethod("use", Service.class, String.class))
                .withArgumentLookup(0)
                .build();

        assertThrows(RuntimeException.class, () -> invoker.invoke(new ArgumentLookupBean(), "only-one-arg"));
    }

    @Test
    @DisplayName("10.3.1 - Unsatisfied looked up bean causes deployment problem when building invoker")
    public void shouldFailBuildForUnsatisfiedLookedUpBean() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(UnsatisfiedLookupBean.class);
        syringe.setup();

        InvokerBuilder<Invoker<UnsatisfiedLookupBean, ?>> builder =
                createInvokerBuilder(CapturedManagedBeans.get(UnsatisfiedLookupBean.class),
                        UnsatisfiedLookupBean.class.getDeclaredMethod("use", MissingService.class));

        assertThrows(DefinitionException.class, () -> builder.withArgumentLookup(0).build());
    }

    @Test
    @DisplayName("10.3.1 - Ambiguous looked up bean causes deployment problem when building invoker")
    public void shouldFailBuildForAmbiguousLookedUpBean() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(AmbiguousLookupBean.class, AmbiguousServiceA.class, AmbiguousServiceB.class);
        syringe.setup();

        InvokerBuilder<Invoker<AmbiguousLookupBean, ?>> builder =
                createInvokerBuilder(CapturedManagedBeans.get(AmbiguousLookupBean.class),
                        AmbiguousLookupBean.class.getDeclaredMethod("use", AmbiguousService.class));

        assertThrows(DefinitionException.class, () -> builder.withArgumentLookup(0).build());
    }

    @Test
    @DisplayName("10.3.1 - @Dependent looked up bean instances are destroyed before invoke() returns")
    public void shouldDestroyDependentLookedUpBeansAfterInvoke() throws Exception {
        DependentLookupService.destroyed.set(0);
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(DependentLookupBean.class, DependentLookupService.class);
        syringe.setup();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(DependentLookupBean.class),
                DependentLookupBean.class.getDeclaredMethod("use", DependentLookupService.class))
                .withArgumentLookup(0)
                .build();

        assertEquals("dependent", invoker.invoke(new DependentLookupBean(), new Object[]{null}));
        assertEquals(1, DependentLookupService.destroyed.get());
    }

    @Test
    @DisplayName("10.3.1 - Invoker.invoke obtains all looked up bean instances without requiring a specific obtain order")
    public void shouldObtainAllLookedUpBeansWithoutAssumingOrder() throws Exception {
        OrderedLookupRecorder.reset();
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(MultiLookupBean.class, FirstLookupService.class, SecondLookupService.class);
        syringe.setup();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(MultiLookupBean.class),
                MultiLookupBean.class.getDeclaredMethod("use", FirstLookupService.class, SecondLookupService.class))
                .withArgumentLookup(0)
                .withArgumentLookup(1)
                .build();

        assertEquals("first-second", invoker.invoke(new MultiLookupBean(), new Object[]{null, null}));

        List<String> obtained = OrderedLookupRecorder.snapshot();
        assertEquals(2, obtained.size());
        assertTrue(obtained.contains("first"));
        assertTrue(obtained.contains("second"));
    }

    @Test
    @DisplayName("10.3.1 - If creating a looked up bean throws, the exception is rethrown by Invoker.invoke()")
    public void shouldRethrowExceptionFromLookedUpBeanCreation() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(FailingLookupTargetBean.class, ExplodingLookupService.class);
        syringe.setup();

        Invoker invoker = createInvokerBuilder(CapturedManagedBeans.get(FailingLookupTargetBean.class),
                FailingLookupTargetBean.class.getDeclaredMethod("use", ExplodingLookupService.class))
                .withArgumentLookup(0)
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invoker.invoke(new FailingLookupTargetBean(), new Object[]{null}));
        assertTrue(containsMessage(ex, "boom-on-create"));
    }

    @Test
    @DisplayName("10.3.1 - Lookup configuration for asynchronous target methods is non-portable")
    public void shouldRejectLookupConfigurationForAsyncTargetMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(AsyncLookupTargetBean.class, BlueService.class);
        syringe.setup();

        InvokerBuilder<Invoker<AsyncLookupTargetBean, ?>> builder =
                createInvokerBuilder(CapturedManagedBeans.get(AsyncLookupTargetBean.class),
                        AsyncLookupTargetBean.class.getDeclaredMethod("asyncUse", Service.class));

        assertThrows(NonPortableBehaviourException.class, () -> builder.withInstanceLookup());
        assertThrows(NonPortableBehaviourException.class, () -> builder.withArgumentLookup(0));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.addExtension(CaptureManagedBeanExtension.class.getName());
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        Class<?>[] allFixtures = new Class<?>[]{
                LookupTargetBean.class,
                ArgumentLookupBean.class,
                BlueService.class,
                RedService.class,
                UnsatisfiedLookupBean.class,
                AmbiguousLookupBean.class,
                AmbiguousServiceA.class,
                AmbiguousServiceB.class,
                DependentLookupBean.class,
                DependentLookupService.class,
                MultiLookupBean.class,
                FirstLookupService.class,
                SecondLookupService.class,
                FailingLookupTargetBean.class,
                ExplodingLookupService.class,
                AsyncLookupTargetBean.class
        };
        for (Class<?> fixture : allFixtures) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <X> InvokerBuilder<Invoker<X, ?>> createInvokerBuilder(ProcessManagedBean<?> pmb, Method method) {
        AnnotatedMethodWrapper wrapper = new AnnotatedMethodWrapper(method, pmb.getAnnotatedBeanClass());
        return (InvokerBuilder) pmb.createInvoker(wrapper);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getBeanInstance(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beans);
        return (T) beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
    }

    private boolean containsMessage(Throwable throwable, String fragment) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
                new HashMap<Class<?>, ProcessManagedBean<?>>();

        static synchronized void reset() {
            BEANS.clear();
        }

        static synchronized void put(Class<?> beanClass, ProcessManagedBean<?> pmb) {
            BEANS.put(beanClass, pmb);
        }

        static synchronized ProcessManagedBean<?> get(Class<?> beanClass) {
            return BEANS.get(beanClass);
        }
    }

    public static class ExampleBuildCompatibleExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(InvokerFactory invokerFactory) {
            // signature-only test fixture
        }
    }

    @ApplicationScoped
    public static class LookupTargetBean {
        static final AtomicInteger sequence = new AtomicInteger();
        final String id = "bean-" + sequence.incrementAndGet();

        public String concat(String a, String b) {
            return id + ":" + a + b;
        }

        public static String staticEcho(String value) {
            return value;
        }
    }

    @ApplicationScoped
    public static class ArgumentLookupBean {
        public String use(@Blue Service service, String input) {
            return service.name() + ":" + input;
        }
    }

    @ApplicationScoped
    public static class UnsatisfiedLookupBean {
        public String use(MissingService service) {
            return service.value();
        }
    }

    @ApplicationScoped
    public static class AmbiguousLookupBean {
        public String use(AmbiguousService service) {
            return service.value();
        }
    }

    @ApplicationScoped
    public static class DependentLookupBean {
        public String use(DependentLookupService service) {
            return service.value();
        }
    }

    @ApplicationScoped
    public static class AsyncLookupTargetBean {
        public CompletionStage<String> asyncUse(@Blue Service service) {
            return CompletableFuture.completedFuture(service.name());
        }
    }

    @ApplicationScoped
    public static class MultiLookupBean {
        public String use(FirstLookupService first, SecondLookupService second) {
            return first.value() + "-" + second.value();
        }
    }

    @ApplicationScoped
    public static class FailingLookupTargetBean {
        public String use(ExplodingLookupService service) {
            return service.value();
        }
    }

    public interface Service {
        String name();
    }

    @Blue
    @Dependent
    public static class BlueService implements Service {
        @Override
        public String name() {
            return "blue";
        }
    }

    @Dependent
    public static class RedService implements Service {
        @Override
        public String name() {
            return "red";
        }
    }

    public interface MissingService {
        String value();
    }

    public interface AmbiguousService {
        String value();
    }

    @Dependent
    public static class AmbiguousServiceA implements AmbiguousService {
        @Override
        public String value() {
            return "a";
        }
    }

    @Dependent
    public static class AmbiguousServiceB implements AmbiguousService {
        @Override
        public String value() {
            return "b";
        }
    }

    @Dependent
    public static class DependentLookupService {
        static final AtomicInteger destroyed = new AtomicInteger();

        String value() {
            return "dependent";
        }

        @PreDestroy
        public void preDestroy() {
            destroyed.incrementAndGet();
        }
    }

    @Dependent
    public static class FirstLookupService {
        public FirstLookupService() {
            OrderedLookupRecorder.record("first");
        }

        String value() {
            return "first";
        }
    }

    @Dependent
    public static class SecondLookupService {
        public SecondLookupService() {
            OrderedLookupRecorder.record("second");
        }

        String value() {
            return "second";
        }
    }

    @Dependent
    public static class ExplodingLookupService {
        public ExplodingLookupService() {
            throw new RuntimeException("boom-on-create");
        }

        String value() {
            return "never";
        }
    }

    public static final class OrderedLookupRecorder {
        private static final List<String> CREATED = Collections.synchronizedList(new ArrayList<String>());

        static void reset() {
            CREATED.clear();
        }

        static void record(String marker) {
            CREATED.add(marker);
        }

        static List<String> snapshot() {
            synchronized (CREATED) {
                return new ArrayList<String>(CREATED);
            }
        }
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Blue {
    }

}
