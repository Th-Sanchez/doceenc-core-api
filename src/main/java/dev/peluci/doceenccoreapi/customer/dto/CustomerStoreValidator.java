package dev.peluci.doceenccoreapi.customer.dto;

import dev.peluci.doceenccoreapi.customer.CustomerType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CustomerStoreValidator implements ConstraintValidator<ValidCustomerStore, CustomerRequest> {


    @Override
    public boolean isValid(CustomerRequest request, ConstraintValidatorContext context) {
        if (request.type() == null) {
            return true;
        }

        boolean wholesale = request.type() == CustomerType.WHOLESALE;
        boolean hasStoreName = request.storeName() != null;

        if (wholesale == hasStoreName) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(wholesale
                        ? "must not be blank for wholesale customers"
                        : "must be empty for retail customers")
                .addPropertyNode("storeName")
                .addConstraintViolation();

        return false;
    }
}
