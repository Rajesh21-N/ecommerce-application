package com.example.ecommerce.service.impl;

import com.example.ecommerce.dto.request.CartItemRequest;
import com.example.ecommerce.dto.response.CartResponse;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.CartMapper;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemRequest request) {
        Cart cart = getOrCreateCart(userId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + request.getProductId() + " not found"));

        CartItem existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
        int desiredQuantity = request.getQuantity() + (existing != null ? existing.getQuantity() : 0);

        if (desiredQuantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
        if (desiredQuantity > product.getStockQuantity()) {
            throw new InsufficientStockException(
                    "Only " + product.getStockQuantity() + " units of '" + product.getName() + "' are available");
        }

        if (existing != null) {
            existing.setQuantity(desiredQuantity);
            existing.setPrice(product.getPrice());
            cartItemRepository.save(existing);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(desiredQuantity)
                    .price(product.getPrice())
                    .build();
            cart.getItems().add(item);
            cartItemRepository.save(item);
        }

        Cart refreshed = cartRepository.findById(cart.getId()).orElseThrow();
        return cartMapper.toResponse(refreshed);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long productId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);

        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in the cart"));

        Product product = item.getProduct();
        if (quantity > product.getStockQuantity()) {
            throw new InsufficientStockException(
                    "Only " + product.getStockQuantity() + " units of '" + product.getName() + "' are available");
        }

        item.setQuantity(quantity);
        item.setPrice(product.getPrice());
        cartItemRepository.save(item);

        Cart refreshed = cartRepository.findById(cart.getId()).orElseThrow();
        return cartMapper.toResponse(refreshed);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in the cart"));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        Cart refreshed = cartRepository.findById(cart.getId()).orElseThrow();
        return cartMapper.toResponse(refreshed);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    /**
     * Every user gets exactly one cart. If, for some legacy reason, it doesn't
     * exist yet (e.g. user created before this logic existed), create it lazily.
     */
    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    throw new ResourceNotFoundException("Cart not found for user " + userId);
                });
    }
}
