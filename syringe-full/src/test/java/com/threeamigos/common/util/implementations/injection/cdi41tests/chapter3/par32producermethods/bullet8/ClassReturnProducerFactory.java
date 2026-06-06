package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ClassReturnProducerFactory {

    @Produces
    DetailedOrder produceOrder() {
        return null;
    }

    public interface Auditable {
    }

    public interface Persistable {
    }

    public static class BaseOrder implements Auditable {
    }

    public static class DetailedOrder extends BaseOrder implements Persistable {
    }
}
