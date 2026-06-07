package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;

/**
 * AfterDeploymentValidation event implementation.
 * 
 * <p>Fired after the container has validated all beans, injection points, and deployment descriptors.
 * Extensions can use this event to:
 * <ul>
 *   <li>Perform additional validation</li>
 *   <li>Register deployment problems via {@link #addDeploymentProblem(Throwable)}</li>
 * </ul>
 *
 * <p>This is the last event fired before the container is ready for use.
 *
 * @see AfterDeploymentValidation
 */
public class AfterDeploymentValidationImpl implements AfterDeploymentValidation, ObserverInvocationLifecycle {

    private final KnowledgeBase knowledgeBase;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public AfterDeploymentValidationImpl(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void addDeploymentProblem(Throwable t) {
        assertObserverInvocationActive();
        if (t == null) {
            knowledgeBase.addError("[AfterDeploymentValidation] Deployment problem from extension");
            return;
        }
        String message = t.getMessage();
        if (message == null || message.isEmpty()) {
            message = t.getClass().getName();
        }
        knowledgeBase.addError("[AfterDeploymentValidation] Deployment problem from extension: " + message);
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
    }

    @Override
    public void endObserverInvocation() {
        observerInvocationActive.set(Boolean.FALSE);
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("AfterDeploymentValidation methods may only be called during observer method invocation");
        }
    }
}
