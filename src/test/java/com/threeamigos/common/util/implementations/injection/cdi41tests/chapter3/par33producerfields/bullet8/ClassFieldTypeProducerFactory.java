package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ClassFieldTypeProducerFactory {

    @Produces
    DetailedOrder producedOrder = null;

    public interface Auditable {
    }

    public interface Traceable extends Auditable {
    }

    public interface Persistable {
    }

    public static class BaseOrder implements Traceable {
    }

    public static class DetailedOrder extends BaseOrder implements Persistable {
    }
}
