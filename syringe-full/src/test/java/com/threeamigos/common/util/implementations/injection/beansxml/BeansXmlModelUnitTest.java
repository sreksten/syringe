package com.threeamigos.common.util.implementations.injection.beansxml;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeansXmlModelUnitTest {

    @Test
    void beanDiscoveryModeDefaultsWhenNullOrBlank() throws Exception {
        BeansXml nullMode = new BeansXml();
        setField(nullMode, "beanDiscoveryMode", null);
        assertEquals("annotated", nullMode.getBeanDiscoveryMode());

        BeansXml blankMode = new BeansXml();
        setField(blankMode, "beanDiscoveryMode", "   ");
        assertEquals("annotated", blankMode.getBeanDiscoveryMode());

        BeansXml explicit = new BeansXml();
        setField(explicit, "beanDiscoveryMode", "all");
        assertEquals("all", explicit.getBeanDiscoveryMode());
    }

    @Test
    void trimFlagAndIsEmptyDetection() throws Exception {
        BeansXml withTrim = new BeansXml();
        setField(withTrim, "trim", new Trim());
        assertTrue(withTrim.isTrimEnabled());
        assertNotNull(withTrim.getTrim());
        assertFalse(withTrim.isEmpty());
        assertNotNull(withTrim.toString());
        assertEquals("Trim{enabled}", new Trim().toString());

        BeansXml empty = new BeansXml();
        setField(empty, "alternatives", null);
        setField(empty, "interceptors", null);
        setField(empty, "decorators", null);
        setField(empty, "scan", null);
        setField(empty, "trim", null);
        assertTrue(empty.isEmpty());
        assertFalse(empty.isTrimEnabled());
    }

    @Test
    void alternativesInterceptorsDecoratorsAndScanReportEmptiness() throws Exception {
        Alternatives alternatives = new Alternatives();
        assertTrue(alternatives.isEmpty());
        addToList(alternatives, "classes", "com.example.Mock");
        addToList(alternatives, "stereotypes", "com.example.MockStereo");
        assertFalse(alternatives.isEmpty());
        assertEquals(Collections.singletonList("com.example.Mock"), alternatives.getClasses());
        assertEquals(Collections.singletonList("com.example.MockStereo"), alternatives.getStereotypes());
        assertThrows(UnsupportedOperationException.class, () -> alternatives.getClasses().add("other"));
        assertNotNull(alternatives.toString());

        Interceptors interceptors = new Interceptors();
        assertTrue(interceptors.isEmpty());
        addToList(interceptors, "classes", "com.example.Interceptor");
        assertFalse(interceptors.isEmpty());
        assertEquals(Collections.singletonList("com.example.Interceptor"), interceptors.getClasses());
        assertThrows(UnsupportedOperationException.class, () -> interceptors.getClasses().add("other"));
        assertNotNull(interceptors.toString());

        Decorators decorators = new Decorators();
        assertTrue(decorators.isEmpty());
        addToList(decorators, "classes", "com.example.Decorator");
        assertFalse(decorators.isEmpty());
        assertEquals(Collections.singletonList("com.example.Decorator"), decorators.getClasses());
        assertThrows(UnsupportedOperationException.class, () -> decorators.getClasses().add("other"));
        assertNotNull(decorators.toString());

        Scan scan = new Scan();
        assertTrue(scan.isEmpty());
        Exclude exclude = new Exclude();
        setField(exclude, "name", "com.example.**");
        addToList(scan, "excludes", exclude);
        assertFalse(scan.isEmpty());
        assertEquals(1, scan.getExcludes().size());
        assertNotNull(scan.toString());
    }

    @Test
    void excludeMatchingAndConditionsCoverAllPatterns() throws Exception {
        Exclude exclude = new Exclude();

        assertFalse(exclude.matches("com.example.Anything"));
        setField(exclude, "name", "com.example.Exact");
        assertEquals("com.example.Exact", exclude.getName());
        assertFalse(exclude.matches(null));
        assertTrue(exclude.matches("com.example.Exact"));
        assertFalse(exclude.matches("com.example.Other"));

        setField(exclude, "name", "com.example.**");
        assertTrue(exclude.matches("com.example.deep.Nested"));
        assertFalse(exclude.matches("org.example.Other"));

        setField(exclude, "name", "com.example.*");
        assertTrue(exclude.matches("com.example.Single"));
        assertFalse(exclude.matches("com.example.multi.Level"));
        assertFalse(exclude.matches("org.example.Different"));

        setField(exclude, "name", "com.example.None");
        assertFalse(exclude.matches("com.sample.None"));

        assertTrue(exclude.isUnconditional());
        IfClassAvailable available = new IfClassAvailable();
        setField(available, "name", "org.junit.jupiter.api.Test");
        IfClassNotAvailable notAvailable = new IfClassNotAvailable();
        setField(notAvailable, "name", "com.example.Missing");
        IfSystemProperty property = new IfSystemProperty();
        setField(property, "name", "env");
        setField(property, "value", "dev");
        addToList(exclude, "ifClassAvailable", available);
        addToList(exclude, "ifClassNotAvailable", notAvailable);
        addToList(exclude, "ifSystemProperty", property);
        assertFalse(exclude.isUnconditional());
        assertEquals("org.junit.jupiter.api.Test", exclude.getIfClassAvailable().get(0).getName());
        assertEquals("com.example.Missing", exclude.getIfClassNotAvailable().get(0).getName());
        assertEquals("env", exclude.getIfSystemProperty().get(0).getName());
        assertEquals("dev", exclude.getIfSystemProperty().get(0).getValue());
        assertNotNull(available.toString());
        assertNotNull(notAvailable.toString());
        assertNotNull(property.toString());
        assertNotNull(exclude.toString());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private <T> void addToList(Object target, String name, T value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        ((List<T>) field.get(target)).add(value);
    }
}
