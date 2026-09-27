package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CartItemRequest;
import com.example.ecommerce.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart(Long userId);
    CartResponse addItem(Long userId, CartItemRequest request);
    CartResponse updateItem(Long userId, Long productId, Integer quantity);
    CartResponse removeItem(Long userId, Long productId);
    void clearCart(Long userId);
}
