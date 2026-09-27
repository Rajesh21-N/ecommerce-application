package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.CartItemRequest;
import com.example.ecommerce.dto.response.CartResponse;
import com.example.ecommerce.service.CartService;
import com.example.ecommerce.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cart", description = "Customer's shopping cart (authenticated customers only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Validated
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get the current user's cart")
    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        return ResponseEntity.ok(cartService.getCart(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Add an item to the cart (increments quantity if already present)")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(SecurityUtils.getCurrentUserId(), request));
    }

    @Operation(summary = "Set the exact quantity of a product already in the cart")
    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(@PathVariable Long productId,
                                                    @RequestParam @Min(1) Integer quantity) {
        return ResponseEntity.ok(cartService.updateItem(SecurityUtils.getCurrentUserId(), productId, quantity));
    }

    @Operation(summary = "Remove a single product from the cart")
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(SecurityUtils.getCurrentUserId(), productId));
    }

    @Operation(summary = "Clear the entire cart")
    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
