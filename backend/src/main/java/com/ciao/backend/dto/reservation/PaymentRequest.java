package com.ciao.backend.dto.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** University test-card checkout. Card fields are validated in memory and never stored. */
public class PaymentRequest {
    @NotNull @Positive
    private Integer reservationId;
    private boolean cancel;
    private String cardNumber;
    private String cardholderName;
    private String expiry;
    private String cvv;
    public Integer getReservationId() { return reservationId; }
    public void setReservationId(Integer reservationId) { this.reservationId = reservationId; }
    public boolean isCancel() { return cancel; }
    public void setCancel(boolean cancel) { this.cancel = cancel; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String value) { cardNumber = value; }
    public String getCardholderName() { return cardholderName; }
    public void setCardholderName(String value) { cardholderName = value; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String value) { expiry = value; }
    public String getCvv() { return cvv; }
    public void setCvv(String value) { cvv = value; }
}
