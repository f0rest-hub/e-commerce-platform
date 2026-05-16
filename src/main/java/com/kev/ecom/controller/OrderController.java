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

    @PostMapping("/create-order")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(principal.getUserId(), request);
    }

    @GetMapping("/get-order/{id}")
    public Mono<OrderResponse> getOrderDetails(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return orderService.getOrderById(principal.getUserId(), id);
    }

    @GetMapping
    public Flux<OrderResponse> getAllOrders(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.getAllOrdersForUser(principal.getUserId(), status);
    }

    @DeleteMapping("/cancel-order/{id}")
    public Flux<OrderResponse> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return orderService.cancelOrder(principal.getUserId(), id);
    }
}
