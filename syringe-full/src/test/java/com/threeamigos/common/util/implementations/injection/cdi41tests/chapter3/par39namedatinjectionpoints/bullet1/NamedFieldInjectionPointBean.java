package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par39namedatinjectionpoints.bullet1;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
public class NamedFieldInjectionPointBean {

    @Inject
    @Named
    PaymentService paymentService;

    public PaymentService getPaymentService() {
        return paymentService;
    }

    public interface PaymentService {
        String id();
    }

    @Dependent
    @Named("paymentService")
    public static class PaymentServiceImpl implements PaymentService {

        @Override
        public String id() {
            return "primary";
        }
    }

    @Dependent
    @Named("otherPaymentService")
    public static class OtherPaymentServiceImpl implements PaymentService {

        @Override
        public String id() {
            return "other";
        }
    }
}
