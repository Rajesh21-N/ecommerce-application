package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CreateOrderRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.entity.*;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.InvalidOrderStatusException;
import com.example.ecommerce.exception.UnauthorizedException;
import com.example.ecommerce.mapper.OrderMapper;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PaymentService paymentService;
    @Mock private EmailService emailService;

    private OrderServiceImpl orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, cartRepository, productRepository,
                paymentService, emailService, new OrderMapper());

        user = User.builder().id(1L).name("Test User").email("test@example.com").role(Role.CUSTOMER).build();
        product = Product.builder().id(1L).name("Test Product").price(BigDecimal.valueOf(200)).stockQuantity(10).build();
    }

    @Test
    void createOrder_withEmptyCart_throwsBadRequestException() {
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Main St");

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty cart");
    }

    @Test
    void createOrder_whenStockInsufficient_throwsInsufficientStockException() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(50).price(product.getPrice()).build();
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(item))).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Main St");

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void createOrder_success_reducesInventoryAndClearsCart() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(3).price(product.getPrice()).build();
        List<CartItem> itemList = new ArrayList<>(List.of(item));
        Cart cart = Cart.builder().id(1L).user(user).items(itemList).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Order savedOrder = Order.builder()
                .id(100L).user(user).totalAmount(BigDecimal.valueOf(600))
                .status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING)
                .shippingAddress("123 Main St").items(new ArrayList<>()).build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        Payment mockPayment = Payment.builder().id(1L).order(savedOrder).amount(BigDecimal.valueOf(600))
                .status(PaymentStatus.PENDING).build();
        when(paymentService.createPaymentForOrder(any(Order.class))).thenReturn(mockPayment);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Main St");

        orderService.createOrder(1L, request);

        // stock reduced by 3
        assertThat(product.getStockQuantity()).isEqualTo(7);
        // cart cleared
        assertThat(cart.getItems()).isEmpty();
        verify(emailService).sendOrderConfirmation(any(Order.class));
    }

    @Test
    void cancelOrder_whenNotOwnedByUser_throwsUnauthorizedException() {
        User otherUser = User.builder().id(2L).build();
        Order order = Order.builder().id(1L).user(otherUser).status(OrderStatus.PENDING)
                .items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void cancelOrder_whenAlreadyDelivered_throwsInvalidOrderStatusException() {
        Order order = Order.builder().id(1L).user(user).status(OrderStatus.DELIVERED)
                .items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                .isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void cancelOrder_restoresStockForEachItem() {
        OrderItem orderItem = OrderItem.builder().id(1L).product(product).quantity(4).price(product.getPrice()).build();
        Order order = Order.builder().id(1L).user(user).status(OrderStatus.PENDING)
                .items(new ArrayList<>(List.of(orderItem))).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrder(1L, 1L);

        assertThat(product.getStockQuantity()).isEqualTo(14); // 10 + 4 restored
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void updateStatus_invalidTransition_throwsInvalidOrderStatusException() {
        Order order = Order.builder().id(1L).user(user).status(OrderStatus.PENDING)
                .items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.DELIVERED); // PENDING -> DELIVERED is not allowed directly

        assertThatThrownBy(() -> orderService.updateStatus(1L, request))
                .isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void updateStatus_validTransition_succeeds() {
        Order order = Order.builder().id(1L).user(user).status(OrderStatus.PENDING)
                .items(new ArrayList<>()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.CONFIRMED);

        orderService.updateStatus(1L, request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
