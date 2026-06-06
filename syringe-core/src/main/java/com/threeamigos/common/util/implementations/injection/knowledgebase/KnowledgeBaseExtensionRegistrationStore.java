package com.threeamigos.common.util.implementations.injection.knowledgebase;

import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class KnowledgeBaseExtensionRegistrationStore {

    private final Map<Class<? extends Annotation>, Set<Annotation>> registeredStereotypes = new ConcurrentHashMap<>();
    private final Set<Class<? extends Annotation>> registeredQualifiers = ConcurrentHashMap.newKeySet();
    private final Map<Class<? extends Annotation>, ScopeMetadata> registeredScopes = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, List<Class<? extends AlterableContext>>> registeredContextImplementations =
            new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, Set<Annotation>> registeredInterceptorBindings = new ConcurrentHashMap<>();
    private final Map<String, AnnotatedType<?>> registeredAnnotatedTypes = new ConcurrentHashMap<>();
    private final Map<String, Extension> registeredAnnotatedTypeSources = new ConcurrentHashMap<>();

    void registerStereotype(Class<? extends Annotation> stereotype, Set<Annotation> definitions) {
        registeredStereotypes.put(stereotype, definitions);
    }

    boolean isStereotypeRegistered(Class<? extends Annotation> stereotype) {
        return registeredStereotypes.containsKey(stereotype);
    }

    Set<Annotation> getStereotypeDefinition(Class<? extends Annotation> stereotype) {
        return registeredStereotypes.get(stereotype);
    }

    Map<Class<? extends Annotation>, Set<Annotation>> getRegisteredStereotypes() {
        return registeredStereotypes;
    }

    void registerQualifier(Class<? extends Annotation> qualifier) {
        registeredQualifiers.add(qualifier);
    }

    boolean isQualifierRegistered(Class<? extends Annotation> qualifier) {
        return registeredQualifiers.contains(qualifier);
    }

    Set<Class<? extends Annotation>> getRegisteredQualifiers() {
        return registeredQualifiers;
    }

    void registerScope(Class<? extends Annotation> scopeType, ScopeMetadata metadata) {
        registeredScopes.put(scopeType, metadata);
    }

    boolean isScopeRegistered(Class<? extends Annotation> scopeType) {
        return registeredScopes.containsKey(scopeType);
    }

    ScopeMetadata getScopeMetadata(Class<? extends Annotation> scopeType) {
        return registeredScopes.get(scopeType);
    }

    Map<Class<? extends Annotation>, ScopeMetadata> getRegisteredScopes() {
        return registeredScopes;
    }

    void addContextImplementation(Class<? extends Annotation> scopeType,
                                  Class<? extends AlterableContext> contextImplementation) {
        registeredContextImplementations
                .computeIfAbsent(scopeType, key -> Collections.synchronizedList(new ArrayList<>()))
                .add(contextImplementation);
    }

    List<Class<? extends AlterableContext>> getContextImplementations(Class<? extends Annotation> scopeType) {
        List<Class<? extends AlterableContext>> implementations = registeredContextImplementations.get(scopeType);
        if (implementations == null || implementations.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (implementations) {
            return Collections.unmodifiableList(new ArrayList<>(implementations));
        }
    }

    void registerInterceptorBinding(Class<? extends Annotation> bindingType, Set<Annotation> definitions) {
        registeredInterceptorBindings.put(bindingType, definitions);
    }

    boolean isInterceptorBindingRegistered(Class<? extends Annotation> bindingType) {
        return registeredInterceptorBindings.containsKey(bindingType);
    }

    Set<Annotation> getInterceptorBindingDefinition(Class<? extends Annotation> bindingType) {
        return registeredInterceptorBindings.get(bindingType);
    }

    Map<Class<? extends Annotation>, Set<Annotation>> getRegisteredInterceptorBindings() {
        return registeredInterceptorBindings;
    }

    boolean hasAnnotatedType(String id) {
        return registeredAnnotatedTypes.containsKey(id);
    }

    void registerAnnotatedType(String id, AnnotatedType<?> type) {
        registeredAnnotatedTypes.put(id, type);
    }

    AnnotatedType<?> getRegisteredAnnotatedType(String id) {
        return registeredAnnotatedTypes.get(id);
    }

    Map<String, AnnotatedType<?>> getRegisteredAnnotatedTypes() {
        return registeredAnnotatedTypes;
    }

    void registerAnnotatedTypeSource(String id, Extension source) {
        registeredAnnotatedTypeSources.put(id, source);
    }

    Extension getRegisteredAnnotatedTypeSource(String id) {
        return registeredAnnotatedTypeSources.get(id);
    }

    void clear() {
        registeredStereotypes.clear();
        registeredQualifiers.clear();
        registeredScopes.clear();
        registeredContextImplementations.clear();
        registeredInterceptorBindings.clear();
        registeredAnnotatedTypes.clear();
        registeredAnnotatedTypeSources.clear();
    }
}
