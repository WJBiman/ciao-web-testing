package com.ciao.backend.pattern.strategy.payment;

import com.ciao.backend.dto.reservation.PaymentRequest;
import com.ciao.backend.entity.Payment;
import com.ciao.backend.entity.Reservation;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Order(1)
public class CreditCardPaymentStrategy implements PaymentProcessingStrategy {
    @Override
    public boolean supports(PaymentRequest request) {
        return request != null;
    }

    @Override
    public Payment process(Reservation reservation, PaymentRequest request) {
        String normalizedCard = request.getCardNumber() == null
                ? ""
                : request.getCardNumber().replace(" ", "").replace("-", "");
        boolean accepted = "4111111111111111".equals(normalizedCard)
                && request.getCardholderName() != null
                && "CIAO TEST".equalsIgnoreCase(request.getCardholderName().trim())
                && "12/30".equals(request.getExpiry())
                && "123".equals(request.getCvv());

        return new Payment(
                reservation,
                reservation.getTotalFare(),
                Payment.PaymentMethod.CARD,
                accepted ? Payment.PaymentStatus.SUCCESS : Payment.PaymentStatus.FAILED,
                "DEMO-" + UUID.randomUUID()
        );
    }
}
