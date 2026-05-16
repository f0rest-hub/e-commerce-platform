package com.kev.ecom.controller;

import com.kev.ecom.dto.order.CreateOrderRequest;
import com.kev.ecom.dto.order.OrderResponse;
import com.kev.ecom.enums.OrderStatus;
import com.kev.ecom.model.auth.AuthenticatedUser;
import com.kev.ecom.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Endpoints for managing orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/create-order")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new order", description = "Places a new order for the authenticated user")
    public Mono<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(principal.getUserId(), request);
    }

    @GetMapping("/get-order/{id}")
    @Operation(summary = "Get order details", description = "Retrieves details of a specific order by ID")
    public Mono<OrderResponse> getOrderDetails(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Parameter(description = "ID of the order to retrieve") @PathVariable Long id) {
        return orderService.getOrderById(principal.getUserId(), id);
    }

    @GetMapping
    @Operation(summary = "Get all orders", description = "Retrieves all orders for the authenticated user, optionally filtered by status")
    public Flux<OrderResponse> getAllOrders(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Parameter(description = "Optional status to filter orders by") @RequestParam(required = false) OrderStatus status) {
        return orderService.getAllOrdersForUser(principal.getUserId(), status);
    }

    @DeleteMapping("/cancel-order/{id}")
    @Operation(summary = "Cancel an order", description = "Cancels a pending order and returns the updated order list")
    public Flux<OrderResponse> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Parameter(description = "ID of the order to cancel") @PathVariable Long id) {
        return orderService.cancelOrder(principal.getUserId(), id);
    }
}
