package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CartItemRequest;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.CartMapper;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;

    private CartServiceImpl cartService;

    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartRepository, cartItemRepository, productRepository, new CartMapper());

        product = Product.builder()
                .id(1L)
                .name("Limited Stock Item")
                .price(BigDecimal.valueOf(500))
                .stockQuantity(5)
                .build();

        cart = Cart.builder().id(1L).items(new ArrayList<>()).build();
    }

    @Test
    void addItem_whenRequestedQuantityExceedsStock_throwsInsufficientStockException() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 1L)).thenReturn(Optional.empty());

        CartItemRequest request = new CartItemRequest();
        request.setProductId(1L);
        request.setQuantity(10); // only 5 in stock

        assertThatThrownBy(() -> cartService.addItem(1L, request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("5");
    }

    @Test
    void addItem_whenCartMissing_throwsResourceNotFoundException() {
        when(cartRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        CartItemRequest request = new CartItemRequest();
        request.setProductId(1L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_whenProductNotInCart_throwsResourceNotFoundException() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(1L, 99L, 2))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItem_whenQuantityExceedsStock_throwsInsufficientStockException() {
        CartItem item = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).price(product.getPrice()).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(1L, 1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItem(1L, 1L, 100))
                .isInstanceOf(InsufficientStockException.class);
    }
}
