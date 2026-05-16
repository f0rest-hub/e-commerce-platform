package com.kev.ecom.exception;

public class OrderCancellationException extends RuntimeException {
    public OrderCancellationException(Long orderId, String currentStatus) {
        super("Order " + orderId + " cannot be cancelled. Current status: " + currentStatus
                + ". Only PENDING orders can be cancelled.");
    }
}


