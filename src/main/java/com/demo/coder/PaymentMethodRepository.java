package com.demo.coder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, String> {
    List<PaymentMethodEntity> findAllByUserId(String userId);
}
