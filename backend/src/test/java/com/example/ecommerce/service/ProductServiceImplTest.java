package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.ProductRequest;
import com.example.ecommerce.dto.response.ProductResponse;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private final ProductMapper productMapper = new ProductMapper();

    @BeforeEach
    void setUp() {
        // manually inject the real mapper since @InjectMocks won't construct non-mock collaborators
        productService = new ProductServiceImpl(productRepository, productMapper);
    }

    @Test
    void createProduct_savesAndReturnsProduct() {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setDescription("A product for testing");
        request.setPrice(BigDecimal.valueOf(100));
        request.setStockQuantity(10);
        request.setCategory("Test");

        Product saved = Product.builder()
                .id(1L)
                .name("Test Product")
                .description("A product for testing")
                .price(BigDecimal.valueOf(100))
                .stockQuantity(10)
                .category("Test")
                .build();

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Test Product");
        assertThat(response.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void getProduct_whenNotFound_throwsResourceNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteProduct_whenNotFound_throwsResourceNotFoundException() {
        when(productRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> productService.deleteProduct(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteProduct_whenExists_deletesSuccessfully() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }
}
