package com.example.ecommerce.service;

import com.example.ecommerce.entity.Order;

public interface EmailService {
    void sendOrderConfirmation(Order order);
    void sendOrderStatusUpdate(Order order);
}
