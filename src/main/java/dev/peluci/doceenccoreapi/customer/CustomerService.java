package dev.peluci.doceenccoreapi.customer;

import dev.peluci.doceenccoreapi.customer.dto.CustomerRequest;
import dev.peluci.doceenccoreapi.customer.dto.CustomerResponse;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customers.findAll(BY_NAME).stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(find(id));
    }

    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer(request.name(), request.type(), request.phone(),
                request.address(), request.storeName(), request.notes());
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = find(id);

        if (!customer.isActive()) {
            throw archived();
        }

        customer.update(request.name(), request.type(), request.phone(),
                request.address(), request.storeName(), request.notes());

        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    public CustomerResponse archive(UUID id) {
        Customer customer = find(id);
        customer.archive();
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    public CustomerResponse restore(UUID id) {
        Customer customer = find(id);
        customer.restore();
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    private Customer find(UUID id) {
        return customers.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found."));
    }

    private static ApiException archived() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CUSTOMER_ARCHIVED,
                "Customer is archived. Restore it before editing.");
    }
}
