package com.ciao.backend.pattern.strategy.payment;

import com.ciao.backend.dto.reservation.PaymentRequest;
import com.ciao.backend.entity.Payment;
import com.ciao.backend.entity.Reservation;

public interface PaymentProcessingStrategy {
    boolean supports(PaymentRequest request);
    Payment process(Reservation reservation, PaymentRequest request);
}
