package com.demo.coder;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {
    CustomerEntity findByEmail(String email);
    CustomerEntity findByCustomerId(String customerId);
}
