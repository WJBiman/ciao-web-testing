package com.ciao.backend.dto.reservation;

public class PaymentResponse {
    private String transactionId;
    private String status;
    private String message;

    public PaymentResponse(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    public String getTransactionId() { return transactionId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}

