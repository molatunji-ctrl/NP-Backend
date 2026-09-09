package org.admin.npapplication.controller;

import org.admin.npapplication.dto.*;
import org.admin.npapplication.model.User;
import org.admin.npapplication.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderDto order = orderService.createOrder(user, request);
        return ResponseEntity.status(201).body(order);
    }

    @PostMapping("/quote")
    public ResponseEntity<CheckoutQuoteDto> getCheckoutQuote(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) CheckoutQuoteRequest request
    ) {
        String promoCode = request == null ? null : request.getPromoCode();
        return ResponseEntity.ok(orderService.getCheckoutQuote(user, promoCode));
    }

    @GetMapping
    public ResponseEntity<Page<OrderDto>> getOrders(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderDto> orders = orderService.getUserOrders(user, pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        OrderDto order = orderService.getOrderById(user, id);
        return ResponseEntity.ok(order);
    }
}
