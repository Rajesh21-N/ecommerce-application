package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CreateOrderRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrder(Long userId, CreateOrderRequest request);
    OrderResponse getOrder(Long userId, Long orderId, boolean isAdmin);
    Page<OrderResponse> getOrdersForUser(Long userId, Pageable pageable);
    Page<OrderResponse> getAllOrders(Pageable pageable);
    OrderResponse cancelOrder(Long userId, Long orderId);
    OrderResponse updateStatus(Long orderId, OrderStatusUpdateRequest request);
}
