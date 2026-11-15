package dev.peluci.doceenccoreapi.customer.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = CustomerStoreValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCustomerStore {

    String message() default "invalid storeName for the customer type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
