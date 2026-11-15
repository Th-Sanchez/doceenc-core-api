package dev.peluci.doceenccoreapi.customer.dto;

import dev.peluci.doceenccoreapi.customer.CustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@ValidCustomerStore
public record CustomerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull CustomerType type,
        @Pattern(regexp = "\\d{10,11}") String phone,
        @Size(max = 255) String address,
        @Size(max = 120) String storeName,
        @Size(max = 1000) String notes) {

    public CustomerRequest {
        name = name == null ? null : name.trim();
        phone = phone == null || phone.isBlank() ? null : phone.replaceAll("\\D", "");
        address = blankToNull(address);
        storeName = blankToNull(storeName);
        notes = blankToNull(notes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
