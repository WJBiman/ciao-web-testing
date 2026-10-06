package com.ciao.backend.pattern.strategy.payment;

import com.ciao.backend.dto.reservation.PaymentRequest;
import com.ciao.backend.entity.Payment;
import com.ciao.backend.entity.Reservation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentProcessingContext {
    private final List<PaymentProcessingStrategy> strategies;

    public PaymentProcessingContext(List<PaymentProcessingStrategy> strategies) {
        this.strategies = strategies;
    }

    public Payment processPayment(Reservation reservation, PaymentRequest request) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No payment strategy supports this request."))
                .process(reservation, request);
    }
}
