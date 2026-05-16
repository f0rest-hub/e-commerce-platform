package com.kev.ecom.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found with ID: " + orderId);
    }
}

