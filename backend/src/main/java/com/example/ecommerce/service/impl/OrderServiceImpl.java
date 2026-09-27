package com.example.ecommerce.service.impl;

import com.example.ecommerce.dto.request.CreateOrderRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.dto.response.OrderResponse;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.OrderStatus;
import com.example.ecommerce.entity.PaymentStatus;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.InvalidOrderStatusException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.exception.UnauthorizedException;
import com.example.ecommerce.mapper.OrderMapper;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.EmailService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final OrderMapper orderMapper;

    /**
     * The full checkout flow, run atomically:
     *  1. Load cart, verify not empty
     *  2. Re-verify stock for every item (stock may have changed since it was added to cart)
     *  3. Calculate total
     *  4. Create order + order items
     *  5. Reduce inventory (locking read to reduce oversell risk under concurrency)
     *  6. Clear cart
     *  7. Create payment record
     *  8. Send confirmation email
     * If any step fails, the whole transaction rolls back.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user " + userId));

        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot place an order with an empty cart");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            // Re-fetch the product to get the latest stock, guarding against
            // stale data and reducing (not eliminating, without DB-level locking
            // support in this setup) the oversell race window.
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product " + cartItem.getProduct().getId() + " no longer exists"));

            if (cartItem.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        "Only " + product.getStockQuantity() + " units of '" + product.getName() + "' are available");
            }

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(lineTotal);

            orderItems.add(OrderItem.builder()
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .price(product.getPrice())
                    .build());

            // Reduce inventory now, inside the same transaction as order creation
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }

        Order order = Order.builder()
                .user(cart.getUser())
                .totalAmount(total)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // Clear the cart now that the order has been placed
        cart.getItems().clear();
        cartRepository.save(cart);

        // Create a payment record (real Razorpay order or mock reference)
        var payment = paymentService.createPaymentForOrder(savedOrder);
        savedOrder.setPayment(payment);

        // Send confirmation email (never breaks the transaction on failure)
        emailService.sendOrderConfirmation(savedOrder);

        log.info("Order {} created for user {} with total {}", savedOrder.getId(), userId, total);

        return orderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You do not have access to this order");
        }

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersForUser(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));

        if (!order.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You do not have access to this order");
        }

        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStatusException(
                    "Order in status " + order.getStatus() + " cannot be cancelled");
        }

        // Restore stock for every item in the cancelled order
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        emailService.sendOrderStatusUpdate(saved);

        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatusUpdateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));

        OrderStatus target = request.getStatus();

        if (!order.getStatus().canTransitionTo(target)) {
            throw new InvalidOrderStatusException(
                    "Cannot transition order from " + order.getStatus() + " to " + target);
        }

        // If admin cancels an order that hadn't already been cancelled, restore stock
        if (target == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(target);
        Order saved = orderRepository.save(order);
        emailService.sendOrderStatusUpdate(saved);

        return orderMapper.toResponse(saved);
    }
}
