package com.threeamigos.common.util.implementations.injection.builtinbeans;

import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in CDI bean for RequestContextController.
 */
public class RequestContextControllerBean implements Bean<RequestContextController> {

    private final ContextManager contextManager;

    public RequestContextControllerBean(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Override
    public Class<?> getBeanClass() {
        return RequestContextController.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public RequestContextController create(CreationalContext<RequestContextController> context) {
        return new Controller(contextManager);
    }

    @Override
    public void destroy(RequestContextController instance, CreationalContext<RequestContextController> context) {
        if (context != null) {
            context.release();
        }
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new HashSet<>();
        types.add(RequestContextController.class);
        types.add(Object.class);
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(Default.Literal.INSTANCE);
        qualifiers.add(Any.Literal.INSTANCE);
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    /**
     * Stateful controller implementation. Cross-thread reuse is intentionally unsupported.
     */
    static final class Controller implements RequestContextController {

        private final ContextManager contextManager;
        private final ThreadLocal<Boolean> activatedByThisController =
                ThreadLocal.withInitial(() -> Boolean.FALSE);

        Controller(ContextManager contextManager) {
            this.contextManager = contextManager;
        }

        @Override
        public boolean activate() {
            if (contextManager.getContext(RequestScoped.class).isActive()) {
                return false;
            }
            contextManager.activateRequest();
            activatedByThisController.set(Boolean.TRUE);
            return true;
        }

        @Override
        public void deactivate() throws ContextNotActiveException {
            if (!contextManager.getContext(RequestScoped.class).isActive()) {
                throw new ContextNotActiveException("No active request context");
            }

            if (Boolean.TRUE.equals(activatedByThisController.get())) {
                contextManager.deactivateRequest();
                activatedByThisController.set(Boolean.FALSE);
            }
        }
    }
}
