package com.ciao.backend.pattern.strategy.payment;

import com.ciao.backend.dto.reservation.PaymentRequest;
import com.ciao.backend.entity.Payment;
import com.ciao.backend.entity.Reservation;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Extension point for a future verified bank-transfer checkout channel. */
@Component
@Order(2)
public class BankTransferPaymentStrategy implements PaymentProcessingStrategy {
    @Override
    public boolean supports(PaymentRequest request) {
        return request != null && request.getCardNumber() != null
                && request.getCardNumber().trim().equalsIgnoreCase("BANK_TRANSFER_DEMO");
    }

    @Override
    public Payment process(Reservation reservation, PaymentRequest request) {
        return new Payment(
                reservation,
                reservation.getTotalFare(),
                Payment.PaymentMethod.BANK_TRANSFER,
                Payment.PaymentStatus.PENDING,
                "BANK-DEMO-" + UUID.randomUUID()
        );
    }
}
