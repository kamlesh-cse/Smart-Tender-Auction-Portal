package com.smartportal.service;

public interface PaymentGateway {
    /**
     * Process a payment of the given amount.
     * @param amount The amount to charge.
     * @return A transaction reference ID if successful.
     * @throws RuntimeException if payment fails.
     */
    String processPayment(Double amount);
}
