package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet10;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class AdditionalParametersDisposerMethodBean {

    public static int disposeCount = 0;
    public static Object lastDisposedInstance = null;
    public static String capturedPrimaryId = null;
    public static String capturedSecondaryId = null;
    public static String capturedPlainId = null;

    public static void reset() {
        disposeCount = 0;
        lastDisposedInstance = null;
        capturedPrimaryId = null;
        capturedSecondaryId = null;
        capturedPlainId = null;
    }

    @Produces
    Product produceProduct() {
        return new Product();
    }

    void disposeProduct(@Disposes Product product,
                        @PrimaryDisposerParam QualifiedDependency primary,
                        @SecondaryDisposerParam QualifiedDependency secondary,
                        PlainDependency plain) {
        disposeCount++;
        lastDisposedInstance = product;
        capturedPrimaryId = primary.id();
        capturedSecondaryId = secondary.id();
        capturedPlainId = plain.id();
    }

    public static class Product {
    }

    public interface QualifiedDependency {
        String id();
    }

    @Dependent
    @PrimaryDisposerParam
    public static class PrimaryQualifiedDependency implements QualifiedDependency {
        @Override
        public String id() {
            return "primary";
        }
    }

    @Dependent
    @SecondaryDisposerParam
    public static class SecondaryQualifiedDependency implements QualifiedDependency {
        @Override
        public String id() {
            return "secondary";
        }
    }

    @Dependent
    public static class PlainDependency {
        String id() {
            return "plain";
        }
    }
}
