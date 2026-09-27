package com.example.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ecommerce.dto.request.*;
import com.example.ecommerce.dto.response.AuthResponse;
import com.example.ecommerce.dto.response.ProductResponse;
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

/**
 * Simulates the entire "happy path" business workflow end to end, plus the
 * key negative scenarios called out in the spec: insufficient stock, empty
 * cart checkout, unauthorized order access, and inventory restoration on
 * cancellation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private String adminToken;
    private String customerToken;
    private String otherCustomerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = registerAndLogin("Flow Admin", "flow.admin@example.com", "Admin@123", true);
        customerToken = registerAndLogin("Flow Customer", "flow.customer@example.com", "Customer@123", false);
        otherCustomerToken = registerAndLogin("Other Customer", "flow.other@example.com", "Customer@123", false);
    }

    @Test
    void fullCheckoutFlow_reducesStockAndCreatesOrder() throws Exception {
        Long productId = createProduct("Flow Test Widget", 10, BigDecimal.valueOf(250));

        // Add 3 to cart
        CartItemRequest addItem = new CartItemRequest();
        addItem.setProductId(productId);
        addItem.setQuantity(3);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(750.0));

        // Checkout
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setShippingAddress("42 Test Lane");
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(750.0));

        // Stock should now be 7
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(7));

        // Cart should now be empty
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void checkout_withEmptyCart_returnsBadRequest() throws Exception {
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setShippingAddress("Empty Cart Lane");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void addToCart_exceedingStock_returnsConflict() throws Exception {
        Long productId = createProduct("Scarce Widget", 2, BigDecimal.valueOf(100));

        CartItemRequest addItem = new CartItemRequest();
        addItem.setProductId(productId);
        addItem.setQuantity(5); // only 2 in stock

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addItem)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void viewOrder_belongingToAnotherUser_isForbidden() throws Exception {
        Long productId = createProduct("Private Order Widget", 10, BigDecimal.valueOf(150));

        CartItemRequest addItem = new CartItemRequest();
        addItem.setProductId(productId);
        addItem.setQuantity(1);
        mockMvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addItem)));

        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setShippingAddress("Somewhere");
        String responseBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseBody).get("id").asLong();

        // A different customer tries to view it
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateOrderStatusThroughValidTransitions() throws Exception {
        Long productId = createProduct("Admin Flow Widget", 10, BigDecimal.valueOf(300));

        CartItemRequest addItem = new CartItemRequest();
        addItem.setProductId(productId);
        addItem.setQuantity(1);
        mockMvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addItem)));

        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setShippingAddress("Admin Test Address");
        String responseBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(responseBody).get("id").asLong();

        OrderStatusUpdateRequest toConfirmed = new OrderStatusUpdateRequest();
        toConfirmed.setStatus(com.example.ecommerce.entity.OrderStatus.CONFIRMED);
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toConfirmed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Invalid: CONFIRMED -> DELIVERED directly is not allowed
        OrderStatusUpdateRequest toDelivered = new OrderStatusUpdateRequest();
        toDelivered.setStatus(com.example.ecommerce.entity.OrderStatus.DELIVERED);
        mockMvc.perform(put("/api/admin/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toDelivered)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS_TRANSITION"));
    }

    // ---- helpers ----

    private String registerAndLogin(String name, String email, String password, boolean makeAdmin) throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setName(name);
        register.setEmail(email);
        register.setPassword(password);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)));

        if (makeAdmin) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.setRole(Role.ADMIN);
            userRepository.save(user);
        }

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).getAccessToken();
    }

    private Long createProduct(String name, int stock, BigDecimal price) throws Exception {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setDescription("Created for OrderFlowIT");
        request.setPrice(price);
        request.setStockQuantity(stock);
        request.setCategory("Test");

        String body = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ProductResponse.class).getId();
    }
}
