package dev.peluci.doceenccoreapi.customer;

import dev.peluci.doceenccoreapi.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerType type;

    @Column(length = 20)
    private String phone;

    @Column
    private String address;

    @Column(name = "store_name", length = 120)
    private String storeName;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Customer() {
    }

    public Customer(String name, CustomerType type, String phone, String address, String storeName, String notes) {
        this.name = name;
        this.type = type;
        this.phone = phone;
        this.address = address;
        this.storeName = storeName;
        this.notes = notes;
    }

    public void update(String name, CustomerType type, String phone, String address, String storeName, String notes) {
        requireConsistentStoreName(type, storeName);
        this.name = name;
        this.type = type;
        this.phone = phone;
        this.address = address;
        this.storeName = storeName;
        this.notes = notes;
    }

    public void archive() {
        this.active = false;
    }

    public void restore() {
        this.active = true;
    }

    private static void requireConsistentStoreName(CustomerType type, String storeName) {
        if ((type == CustomerType.WHOLESALE) == (storeName == null)) {
            throw new IllegalArgumentException("storeName is required for WHOLESALE and forbidden for RETAIL");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CustomerType getType() {
        return type;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }
}
