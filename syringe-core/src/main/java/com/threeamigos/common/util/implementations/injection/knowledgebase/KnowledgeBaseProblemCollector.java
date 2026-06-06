package com.threeamigos.common.util.implementations.injection.knowledgebase;

import java.util.ArrayList;
import java.util.List;

final class KnowledgeBaseProblemCollector {

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> definitionErrors = new ArrayList<>();
    private final List<String> deploymentErrors = new ArrayList<>();
    private final List<String> injectionErrors = new ArrayList<>();
    private final List<String> illegalProductErrors = new ArrayList<>();

    void addWarning(String warning) {
        warnings.add(warning);
    }

    List<String> getWarnings() {
        return warnings;
    }

    void addError(String error) {
        errors.add(error);
    }

    List<String> getErrors() {
        return errors;
    }

    void addDefinitionError(String error) {
        definitionErrors.add(error);
    }

    List<String> getDefinitionErrors() {
        return definitionErrors;
    }

    void addDeploymentError(String error) {
        deploymentErrors.add(error);
    }

    List<String> getDeploymentErrors() {
        return deploymentErrors;
    }

    void addInjectionError(String error) {
        injectionErrors.add(error);
    }

    List<String> getInjectionErrors() {
        return injectionErrors;
    }

    void addIllegalProductError(String error) {
        illegalProductErrors.add(error);
    }

    List<String> getIllegalProductErrors() {
        return illegalProductErrors;
    }

    boolean hasErrors() {
        return !definitionErrors.isEmpty() || !injectionErrors.isEmpty() || !errors.isEmpty();
    }

    void clear() {
        warnings.clear();
        errors.clear();
        definitionErrors.clear();
        deploymentErrors.clear();
        injectionErrors.clear();
        illegalProductErrors.clear();
    }
}
