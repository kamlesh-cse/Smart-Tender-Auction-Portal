package com.smartportal.service;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class MockPaymentGatewayImpl implements PaymentGateway {
    
    @Override
    public String processPayment(Double amount) {
        // Simulate external API call delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        
        // Mock payment logic: succeed most of the time
        if (amount != null && amount > 0) {
            return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        
        throw new RuntimeException("Invalid payment amount.");
    }
}
