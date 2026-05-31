package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Implementation of jakarta.enterprise.inject.spi.CDI for static container access.
 *
 * <p>This class provides the CDI.current() entry point for getting a BeanManager
 * reference when injection is not available.
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * BeanManager manager = CDI.current().getBeanManager();
 * MyBean bean = CDI.current().select(MyBean.class).get();
 * </pre>
 *
 * <p><b>CDI 4.1 Compliance:</b>
 * <ul>
 *   <li>Section 12.1: Accessing the BeanManager statically via CDI.current()</li>
 *   <li>Section 5.6: Programmatic lookup via Instance<T> methods</li>
 * </ul>
 *
 * @author Stefano Reksten
 */
public class CDIImpl extends CDI<Object> {

    private static final BooleanSupplier ALWAYS_ALLOWED = () -> true;

    private static final Set<String> BEAN_CONTAINER_METHOD_SIGNATURES = beanContainerMethodSignatures();

    private final BeanManagerImpl beanManager;
    private final BeanManager beanManagerView;
    private volatile Instance<Object> rootInstance;
    private final BooleanSupplier accessGuard;

    /**
     * Creates a new CDI implementation wrapping the given BeanManager.
     *
     * @param beanManager The BeanManager to wrap
     */
    public CDIImpl(BeanManagerImpl beanManager) {
        this(beanManager, false, ALWAYS_ALLOWED);
    }

    public CDIImpl(BeanManagerImpl beanManager, boolean cdiLiteMode, BooleanSupplier accessGuard) {
        if (beanManager == null) {
            throw new IllegalArgumentException("beanManager cannot be null");
        }
        this.beanManager = beanManager;
        this.accessGuard = accessGuard != null ? accessGuard : ALWAYS_ALLOWED;
        this.beanManagerView = cdiLiteMode ? createLiteBeanManagerView(beanManager) : beanManager;

    }

    /**
     * Returns the BeanManager for programmatic CDI access.
     *
     * <p>Application components which cannot get a BeanManager reference
     * via injection nor JNDI lookup can get the reference from the
     * jakarta.enterprise.inject.spi.CDI class via a static method call:
     * <pre>
     * BeanManager manager = CDI.current().getBeanManager();
     * </pre>
     *
     * @return The BeanManager
     */
    @Override
    public BeanManager getBeanManager() {
        ensurePortableAccessWindow();
        return beanManagerView;
    }

    // ========================================================================
    // Instance<T> Implementation - Delegate to root instance
    // ========================================================================

    /**
     * Gets a child Instance for the given required type and qualifiers.
     *
     * @param subtype The required type
     * @param qualifiers The required qualifiers
     * @param <U> The type
     * @return The child Instance
     */
    @Override
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        ensurePortableAccessWindow();
        return rootInstance().select(subtype, qualifiers);
    }

    /**
     * Gets a child Instance for the given required type and qualifiers.
     *
     * @param subtype The required type
     * @param qualifiers The required qualifiers
     * @param <U> The type
     * @return The child Instance
     */
    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        ensurePortableAccessWindow();
        return rootInstance().select(subtype, qualifiers);
    }

    /**
     * Determines if there is more than one bean that matches the required type and qualifiers
     * and is eligible for injection.
     *
     * @return true if there is more than one matching bean
     */
    @Override
    public boolean isAmbiguous() {
        ensurePortableAccessWindow();
        return rootInstance().isAmbiguous();
    }

    /**
     * Determines if there is no bean that matches the required type and qualifiers
     * and is eligible for injection.
     *
     * @return true if there is no matching bean
     */
    @Override
    public boolean isUnsatisfied() {
        ensurePortableAccessWindow();
        return rootInstance().isUnsatisfied();
    }

    /**
     * Determines if there is exactly one bean that matches the required type and qualifiers
     * and is eligible for injection.
     *
     * @return true if there is exactly one matching bean
     */
    @Override
    public boolean isResolvable() {
        ensurePortableAccessWindow();
        return rootInstance().isResolvable();
    }

    /**
     * When called, provides back the instance associated with this Instance.
     *
     * @return The instance
     * @throws jakarta.enterprise.inject.UnsatisfiedResolutionException if no bean matches
     * @throws jakarta.enterprise.inject.AmbiguousResolutionException if multiple beans match
     */
    @Override
    public Object get() {
        ensurePortableAccessWindow();
        return rootInstance().get();
    }

    /**
     * Returns an Iterator over all beans that match the required type and qualifiers.
     *
     * @return Iterator of matching bean instances
     */
    @Override
    public @Nonnull Iterator<Object> iterator() {
        ensurePortableAccessWindow();
        return rootInstance().iterator();
    }

    /**
     * Returns a Stream over all beans that match the required type and qualifiers.
     *
     * @return Stream of matching bean instances
     */
    @Override
    public Stream<Object> stream() {
        ensurePortableAccessWindow();
        return rootInstance().stream();
    }

    /**
     * Gets an Instance.Handle for the bean instance.
     *
     * @return The Handle
     */
    @Override
    public Handle<Object> getHandle() {
        ensurePortableAccessWindow();
        return rootInstance().getHandle();
    }

    /**
     * Gets Handles for all beans that match the required type and qualifiers.
     *
     * @return Iterable of Handles
     */
    @Override
    public Iterable<? extends Handle<Object>> handles() {
        ensurePortableAccessWindow();
        return rootInstance().handles();
    }

    /**
     * Returns a Stream of Handles for all beans that match the required type and qualifiers.
     *
     * @return Stream of Handles
     */
    @Override
    public Stream<? extends Handle<Object>> handlesStream() {
        ensurePortableAccessWindow();
        return rootInstance().handlesStream();
    }

    /**
     * Explicitly destroys an instance, calling any @PreDestroy methods.
     *
     * @param instance The instance to destroy
     */
    @Override
    public void destroy(Object instance) {
        ensurePortableAccessWindow();
        rootInstance().destroy(instance);
    }

    /**
     * Gets a child Instance for the given qualifiers.
     *
     * @param qualifiers The qualifiers
     * @return The child Instance
     */
    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        ensurePortableAccessWindow();
        return rootInstance().select(qualifiers);
    }

    private void ensurePortableAccessWindow() {
        if (!accessGuard.getAsBoolean()) {
            throw new NonPortableBehaviourException(
                "CDI methods may only be invoked after BeforeBeanDiscovery and before BeforeShutdown");
        }
    }

    private Instance<Object> rootInstance() {
        Instance<Object> instance = rootInstance;
        if (instance == null) {
            synchronized (this) {
                instance = rootInstance;
                if (instance == null) {
                    instance = beanManager.createInstance();
                    rootInstance = instance;
                }
            }
        }
        return instance;
    }

    private BeanManager createLiteBeanManagerView(final BeanManager delegate) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(delegate, args);
            }
            if (!isBeanContainerMethod(method)) {
                throw new NonPortableBehaviourException(
                    "Invoking BeanManager methods not inherited from BeanContainer is non-portable in CDI Lite: " +
                            method);
            }
            return method.invoke(delegate, args);
        };
        return (BeanManager) Proxy.newProxyInstance(
            BeanManager.class.getClassLoader(),
            new Class[]{BeanManager.class},
            handler
        );
    }

    private static Set<String> beanContainerMethodSignatures() {
        Set<String> signatures = new HashSet<>();
        for (Method method : BeanContainer.class.getMethods()) {
            signatures.add(signatureOf(method));
        }
        return signatures;
    }

    private static boolean isBeanContainerMethod(Method method) {
        return BEAN_CONTAINER_METHOD_SIGNATURES.contains(signatureOf(method));
    }

    private static String signatureOf(Method method) {
        return method.getName() + "#" + Arrays.toString(method.getParameterTypes());
    }
}
