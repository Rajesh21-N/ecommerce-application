package com.example.ecommerce.service.impl;

import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.Payment;
import com.example.ecommerce.entity.PaymentStatus;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.service.PaymentService;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Payment gateway abstraction.
 *
 * When app.razorpay.enabled=true and real credentials are supplied via
 * RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET, this creates a real Razorpay order.
 *
 * When disabled (default for local/dev without credentials), it falls back to
 * a deterministic mock payment so the checkout flow still works end-to-end
 * locally without any external dependency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${app.razorpay.enabled}")
    private boolean razorpayEnabled;

    @Value("${app.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Override
    @Transactional
    public Payment createPaymentForOrder(Order order) {
        String gatewayReference = razorpayEnabled
                ? createRazorpayOrder(order)
                : createMockPaymentReference(order);

        Payment payment = Payment.builder()
                .order(order)
                .paymentId(gatewayReference)
                .amount(order.getTotalAmount())
                .paymentMethod(razorpayEnabled ? "RAZORPAY" : "MOCK")
                .status(PaymentStatus.PENDING)
                .build();

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment markSuccess(Long paymentId, String gatewayPaymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
        payment.setStatus(PaymentStatus.SUCCESS);
        if (gatewayPaymentId != null) {
            payment.setPaymentId(gatewayPaymentId);
        }
        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment markFailed(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " not found"));
        payment.setStatus(PaymentStatus.FAILED);
        return paymentRepository.save(payment);
    }

    private String createRazorpayOrder(Order order) {
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            // Razorpay expects the amount in the smallest currency unit (paise for INR)
            orderRequest.put("amount", order.getTotalAmount().multiply(java.math.BigDecimal.valueOf(100)).intValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "order_rcpt_" + order.getId());
            com.razorpay.Order rzpOrder = client.orders.create(orderRequest);
            return rzpOrder.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed, falling back to mock reference: {}", e.getMessage());
            return createMockPaymentReference(order);
        }
    }

    private String createMockPaymentReference(Order order) {
        return "mock_pay_" + order.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
