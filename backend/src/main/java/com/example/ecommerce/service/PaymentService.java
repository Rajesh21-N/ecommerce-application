package com.example.ecommerce.service;

import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.Payment;

public interface PaymentService {
    Payment createPaymentForOrder(Order order);
    Payment markSuccess(Long paymentId, String gatewayPaymentId);
    Payment markFailed(Long paymentId);
}
