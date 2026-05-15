package com.kev.ecom.controller;

import com.kev.ecom.dto.order.CreateOrderRequest;
import com.kev.ecom.dto.order.OrderResponse;
import com.kev.ecom.enums.OrderStatus;
import com.kev.ecom.model.auth.AuthenticatedUser;
import com.kev.ecom.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    // POST /api/orders
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(principal.getUserId(), request);
    }

    // GET /api/orders/{id}
    @GetMapping("/{id}")
    public Mono<OrderResponse> getOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return orderService.getOrderById(principal.getUserId(), id);
    }

    // GET /api/orders?status=PENDING  — returns only the caller's orders
    @GetMapping
    public Flux<OrderResponse> getMyOrders(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.getOrdersForUser(principal.getUserId(), status);
    }

    // DELETE /api/orders/{id}  — cancels if PENDING
    @DeleteMapping("/{id}")
    public Mono<OrderResponse> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return orderService.cancelOrder(principal.getUserId(), id);
    }
}
