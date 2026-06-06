package com.threeamigos.common.util.implementations.injection.discovery;

/**
 * CDI 4.1 Bean Archive Detection Rules:
 * 1. Explicit Bean Archive
 * A JAR/directory is an explicit bean archive if it contains a beans.xml file with:
 * bean-discovery-mode="all", OR
 * No bean-discovery-mode attribute (defaults to all in CDI 1.1+)
 * → ALL classes with suitable constructors are beans (no annotations required)
 * 1.a Trimmed Explicit Bean Archive
 * Explicit archive with <trim/> present. Behaves like explicit discovery, but ONLY
 * classes with bean-defining annotations are discovered (effectively IMPLICIT).
 * 2. Implicit Bean Archive
 * A JAR/directory is an implicit bean archive if:
 * It contains a beans.xml with bean-discovery-mode="annotated", OR
 * It has NO beans.xml but contains at least one class with a bean-defining annotation
 * → ONLY classes with bean-defining annotations are beans
 * 3. Not a Bean Archive
 * A JAR/directory is NOT a bean archive if:
 * It has beans.xml with bean-discovery-mode="none", OR
 * It has no beans.xml AND no classes with bean-defining annotations
 * → No beans discovered
 */
public enum BeanArchiveMode {

    IMPLICIT,
    EXPLICIT,
    TRIMMED,
    NONE

}
