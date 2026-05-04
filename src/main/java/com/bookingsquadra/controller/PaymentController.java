package com.bookingsquadra.controller;

import com.bookingsquadra.dto.CheckoutRequestDto;
import com.bookingsquadra.dto.CheckoutResponseDto;
import com.bookingsquadra.dto.PixCodeDto;
import com.bookingsquadra.dto.RefundResponseDto;
import com.bookingsquadra.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout")
    public CheckoutResponseDto checkout(@Valid @RequestBody CheckoutRequestDto body) {
        return paymentService.checkout(body);
    }

    @GetMapping("/{bookingId}/pix")
    public PixCodeDto pix(@PathVariable UUID bookingId) {
        return paymentService.getPixCode(bookingId);
    }

    @PostMapping("/{bookingId}/refund")
    public RefundResponseDto refund(@PathVariable UUID bookingId) {
        return paymentService.refundCurrentUserBooking(bookingId);
    }
}
