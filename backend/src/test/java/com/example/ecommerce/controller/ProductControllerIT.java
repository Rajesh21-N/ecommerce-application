package com.example.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ecommerce.dto.request.LoginRequest;
import com.example.ecommerce.dto.request.ProductRequest;
import com.example.ecommerce.dto.request.RegisterRequest;
import com.example.ecommerce.dto.response.AuthResponse;
import com.example.ecommerce.entity.Role;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        // Register a customer normally
        RegisterRequest customerReq = new RegisterRequest();
        customerReq.setName("Product Test Customer");
        customerReq.setEmail("product.customer@example.com");
        customerReq.setPassword("Customer@123");
        String customerBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerReq)))
                .andReturn().getResponse().getContentAsString();
        customerToken = objectMapper.readValue(customerBody, AuthResponse.class).getAccessToken();

        // Register a user then promote to ADMIN directly via repository (simulating a seeded admin)
        RegisterRequest adminReq = new RegisterRequest();
        adminReq.setName("Product Test Admin");
        adminReq.setEmail("product.admin@example.com");
        adminReq.setPassword("Admin@123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminReq)));

        User adminUser = userRepository.findByEmail("product.admin@example.com").orElseThrow();
        adminUser.setRole(Role.ADMIN);
        userRepository.save(adminUser);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("product.admin@example.com");
        loginRequest.setPassword("Admin@123");
        String adminBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readValue(adminBody, AuthResponse.class).getAccessToken();
    }

    @Test
    void getAllProducts_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void createProduct_asCustomer_isForbidden() throws Exception {
        ProductRequest request = buildProductRequest();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_withoutAuth_isUnauthorized() throws Exception {
        ProductRequest request = buildProductRequest();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_asAdmin_succeeds() throws Exception {
        ProductRequest request = buildProductRequest();

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Test Product"));
    }

    @Test
    void getProduct_whenNotFound_returns404WithCleanBody() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createProduct_withInvalidPrice_returnsValidationError() throws Exception {
        ProductRequest request = buildProductRequest();
        request.setPrice(BigDecimal.valueOf(-5));

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    private ProductRequest buildProductRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("Integration Test Product");
        request.setDescription("Created during integration test");
        request.setPrice(BigDecimal.valueOf(999));
        request.setStockQuantity(25);
        request.setCategory("Test");
        return request;
    }
}
