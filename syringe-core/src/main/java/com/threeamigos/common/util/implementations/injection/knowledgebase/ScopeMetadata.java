package com.threeamigos.common.util.implementations.injection.knowledgebase;

import java.lang.annotation.Annotation;

/**
 * Metadata for programmatically registered scopes.
 *
 * <p>Stores information about scopes registered via BeforeBeanDiscovery.addScope():
 * <ul>
 *   <li>scopeType - the scope annotation class</li>
 *   <li>normal - whether it's a normal scope (proxied) or pseudo-scope</li>
 *   <li>passivating - whether instances can be passivated (serialized)</li>
 * </ul>
 */
public class ScopeMetadata {

    private final Class<? extends Annotation> scopeType;
    private final boolean normal;
    private final boolean passivating;

    public ScopeMetadata(Class<? extends Annotation> scopeType, boolean normal, boolean passivating) {
        this.scopeType = scopeType;
        this.normal = normal;
        this.passivating = passivating;
    }

    public Class<? extends Annotation> getScopeType() {
        return scopeType;
    }

    public boolean isNormal() {
        return normal;
    }

    public boolean isPassivating() {
        return passivating;
    }

    @Override
    public String toString() {
        return "ScopeMetadata{" +
               "scopeType=" + scopeType.getSimpleName() +
               ", normal=" + normal +
               ", passivating=" + passivating +
               '}';
    }
}
