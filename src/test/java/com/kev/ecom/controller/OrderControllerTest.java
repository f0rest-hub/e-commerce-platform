package com.kev.ecom.controller;

import com.kev.ecom.config.SecurityConfig;
import com.kev.ecom.dto.order.CreateOrderRequest;
import com.kev.ecom.dto.order.OrderResponse;
import com.kev.ecom.enums.OrderStatus;
import com.kev.ecom.exception.OrderCancellationException;
import com.kev.ecom.exception.OrderNotFoundException;
import com.kev.ecom.model.auth.AuthenticatedUser;
import com.kev.ecom.service.OrderService;
import com.kev.ecom.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

@WebFluxTest(controllers = OrderController.class)
@Import(SecurityConfig.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean
    OrderService  orderService;

    @MockitoBean
    JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.generateToken(anyLong(), anyString())).thenReturn("mock-token");
        when(jwtUtil.isTokenValid(anyString())).thenReturn(true);
        when(jwtUtil.extractEmail(anyString())).thenReturn("test@example.com");
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    private static final Long   USER_ID  = 1L;
    private static final Long   ORDER_ID = 10L;
    private static final String BASE     = "/api/orders";

    // ── Helper: inject a mock AuthenticatedUser into the security context ─────

    private WebTestClient authenticatedClient() {
        AuthenticatedUser principal = new AuthenticatedUser(USER_ID, "test@example.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        return webClient.mutateWith(mockAuthentication(auth))
                .mutate()
                .defaultHeader("Authorization", "Bearer " + jwtUtil.generateToken(USER_ID, "test@example.com"))
                .build();
    }

    private OrderResponse pendingResponse() {
        return OrderResponse.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .items(List.of(
                        OrderResponse.OrderItemResponse.builder()
                                .itemId("SKU-1")
                                .itemName("Widget")
                                .quantity(2)
                                .unitPrice(new BigDecimal("25.00"))
                                .subtotal(new BigDecimal("50.00"))
                                .build()))
                .build();
    }

    private OrderResponse cancelledResponse() {
        OrderResponse r = pendingResponse();
        r.setStatus(OrderStatus.CANCELLED);
        return r;
    }

    @Nested
    @DisplayName("POST /create-order")
    class CreateOrder {

        @Test
        @DisplayName("201 with OrderResponse on valid request")
        void success() {
            when(orderService.createOrder(eq(USER_ID), any(CreateOrderRequest.class)))
                    .thenReturn(Mono.just(pendingResponse()));

            authenticatedClient()
                    .post().uri(BASE + "/create-order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"items":[{
                              "item_id":"SKU-1",
                              "item_name":"Widget",
                              "quantity":2,
                              "unit_price":25.00
                            }]}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(OrderResponse.class)
                    .value(r -> {
                        assertThat(r.getOrderId()).isEqualTo(ORDER_ID);
                        assertThat(r.getStatus()).isEqualTo(OrderStatus.PENDING);
                        assertThat(r.getTotalAmount()).isEqualByComparingTo("50.00");
                        assertThat(r.getItems()).hasSize(1);
                    });
        }

        @Test
        @DisplayName("400 when items list is empty")
        void emptyItems() {
            authenticatedClient()
                    .post().uri(BASE + "/create-order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"items":[]}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("401 when request has no auth token")
        void unauthenticated() {
            webClient.post().uri(BASE + "/create-order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {"items":[{"item_id":"SKU-1","item_name":"W","quantity":1,"unit_price":1.00}]}
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("GET /get-order/{id}")
    class GetOrderDetails {

        @Test
        @DisplayName("200 with order details for the owner")
        void success() {
            when(orderService.getOrderById(USER_ID, ORDER_ID))
                    .thenReturn(Mono.just(pendingResponse()));

            authenticatedClient()
                    .get().uri(BASE + "/get-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(OrderResponse.class)
                    .value(r -> assertThat(r.getOrderId()).isEqualTo(ORDER_ID));
        }

        @Test
        @DisplayName("404 when order not found")
        void notFound() {
            when(orderService.getOrderById(USER_ID, ORDER_ID))
                    .thenReturn(Mono.error(new OrderNotFoundException(ORDER_ID)));

            authenticatedClient()
                    .get().uri(BASE + "/get-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated() {
            webClient.get().uri(BASE + "/get-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("GET /")
    class GetAllOrders {

        @Test
        @DisplayName("200 with all orders when no status filter")
        void allOrders() {
            when(orderService.getAllOrdersForUser(USER_ID, null))
                    .thenReturn(Flux.just(pendingResponse()));

            authenticatedClient()
                    .get().uri(BASE)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(OrderResponse.class)
                    .hasSize(1);
        }

        @Test
        @DisplayName("200 with filtered orders when status param is provided")
        void filteredByStatus() {
            when(orderService.getAllOrdersForUser(USER_ID, OrderStatus.PENDING))
                    .thenReturn(Flux.just(pendingResponse()));

            authenticatedClient()
                    .get().uri(BASE + "?status=PENDING")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(OrderResponse.class)
                    .value(list -> assertThat(list).allMatch(
                            r -> r.getStatus() == OrderStatus.PENDING));
        }

        @Test
        @DisplayName("200 with empty list when user has no orders")
        void emptyList() {
            when(orderService.getAllOrdersForUser(USER_ID, null))
                    .thenReturn(Flux.empty());

            authenticatedClient()
                    .get().uri(BASE)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(OrderResponse.class)
                    .hasSize(0);
        }
    }

    // ── DELETE /api/orders/cancel-order/{id} ──────────────────────────────────

    @Nested
    @DisplayName("DELETE /cancel-order/{id}")
    class CancelOrder {

        @Test
        @DisplayName("200 with updated order list after cancellation")
        void success() {
            when(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .thenReturn(Flux.just(cancelledResponse()));

            authenticatedClient()
                    .delete().uri(BASE + "/cancel-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(OrderResponse.class)
                    .value(list -> assertThat(list.get(0).getStatus())
                            .isEqualTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("404 when order not found")
        void notFound() {
            when(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .thenReturn(Flux.error(new OrderNotFoundException(ORDER_ID)));

            authenticatedClient()
                    .delete().uri(BASE + "/cancel-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("422 when order is not in PENDING status")
        void notCancellable() {
            when(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .thenReturn(Flux.error(new OrderCancellationException(ORDER_ID, "PROCESSING")));

            authenticatedClient()
                    .delete().uri(BASE + "/cancel-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("401 when unauthenticated")
        void unauthenticated() {
            webClient.delete().uri(BASE + "/cancel-order/" + ORDER_ID)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}