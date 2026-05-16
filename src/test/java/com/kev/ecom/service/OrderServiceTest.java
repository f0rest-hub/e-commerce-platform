package com.kev.ecom.service;

import com.kev.ecom.dto.order.CreateOrderRequest;
import com.kev.ecom.dto.order.OrderResponse;
import com.kev.ecom.enums.OrderStatus;
import com.kev.ecom.exception.OrderCancellationException;
import com.kev.ecom.exception.OrderNotFoundException;
import com.kev.ecom.model.order.Order;
import com.kev.ecom.model.order.OrderItem;
import com.kev.ecom.model.order.OrderItemMapping;
import com.kev.ecom.repository.OrderItemMappingRepository;
import com.kev.ecom.repository.OrderItemRepository;
import com.kev.ecom.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock OrderRepository            orderRepository;
    @Mock OrderItemRepository        orderItemRepository;
    @Mock OrderItemMappingRepository mappingRepository;

    @InjectMocks OrderService orderService;

    private static final Long USER_ID  = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long ITEM_ID  = 100L;

    private Order pendingOrder() {
        return Order.builder()
                .id(ORDER_ID)
                .userId(USER_ID)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Order orderWithStatus(OrderStatus status) {
        Order o = pendingOrder();
        o.setStatus(status);
        return o;
    }

    private OrderItem savedItem() {
        return OrderItem.builder()
                .id(ITEM_ID)
                .itemId("SKU-1")
                .itemName("Widget")
                .quantity(2)
                .unitPrice(new BigDecimal("25.00"))
                .build();
    }

    private CreateOrderRequest buildRequest() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setItemId("SKU-1");
        item.setItemName("Widget");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("25.00"));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(new java.util.ArrayList<>(List.of(item)));  // mutable — safe to add to
        return req;
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("saves order, items, mappings and returns response")
        void success() {
            Order saved = pendingOrder();
            OrderItem item = savedItem();
            OrderItemMapping mapping = OrderItemMapping.builder()
                    .id(1L).orderId(ORDER_ID).itemId(ITEM_ID).build();

            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(saved));
            when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.just(item));
            when(mappingRepository.saveAll(anyList())).thenReturn(Flux.just(mapping));

            StepVerifier.create(orderService.createOrder(USER_ID, buildRequest()))
                    .assertNext(response -> {
                        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
                        assertThat(response.getUserId()).isEqualTo(USER_ID);
                        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
                        assertThat(response.getTotalAmount()).isEqualByComparingTo("50.00");
                        assertThat(response.getItems()).hasSize(1);
                        assertThat(response.getItems().get(0).getItemName()).isEqualTo("Widget");
                        assertThat(response.getItems().get(0).getSubtotal())
                                .isEqualByComparingTo("50.00");
                    })
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(orderItemRepository).saveAll(anyList());
            verify(mappingRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("calculates total correctly from multiple items")
        void calculatesTotal() {
            CreateOrderRequest.OrderItemRequest item1 = new CreateOrderRequest.OrderItemRequest();
            item1.setItemId("SKU-1");
            item1.setItemName("Widget");
            item1.setQuantity(2);
            item1.setUnitPrice(new BigDecimal("25.00"));

            CreateOrderRequest.OrderItemRequest item2 = new CreateOrderRequest.OrderItemRequest();
            item2.setItemId("SKU-2");
            item2.setItemName("Gadget");
            item2.setQuantity(3);
            item2.setUnitPrice(new BigDecimal("10.00"));

            // Use ArrayList explicitly so the list is mutable — List.of() is immutable
            CreateOrderRequest req = new CreateOrderRequest();
            req.setItems(new java.util.ArrayList<>(List.of(item1, item2))); // 2x25 + 3x10 = 80.00

            OrderItem item1Saved = savedItem();
            OrderItem item2Saved = OrderItem.builder()
                    .id(101L).itemId("SKU-2").itemName("Gadget")
                    .quantity(3).unitPrice(new BigDecimal("10.00")).build();

            // Capture the saved order to verify total
            when(orderRepository.save(argThat(o -> o.getTotalAmount()
                    .compareTo(new BigDecimal("80.00")) == 0)))
                    .thenReturn(Mono.just(Order.builder()
                            .id(ORDER_ID).userId(USER_ID).status(OrderStatus.PENDING)
                            .totalAmount(new BigDecimal("80.00"))
                            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                            .build()));
            when(orderItemRepository.saveAll(anyList()))
                    .thenReturn(Flux.just(item1Saved, item2Saved));
            when(mappingRepository.saveAll(anyList())).thenReturn(Flux.empty());

            StepVerifier.create(orderService.createOrder(USER_ID, req))
                    .assertNext(r -> assertThat(r.getTotalAmount())
                            .isEqualByComparingTo("80.00"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("propagates error when orderRepository.save fails")
        void repositoryError() {
            when(orderRepository.save(any())).thenReturn(Mono.error(new RuntimeException("DB down")));

            StepVerifier.create(orderService.createOrder(USER_ID, buildRequest()))
                    .expectErrorMessage("DB down")
                    .verify();

            verify(orderItemRepository, never()).saveAll(anyList());
        }
    }

    // ── getOrderById ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrderById()")
    class GetOrderById {

        @Test
        @DisplayName("returns order with items for the owner")
        void success() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pendingOrder()));
            when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(Flux.just(savedItem()));

            StepVerifier.create(orderService.getOrderById(USER_ID, ORDER_ID))
                    .assertNext(r -> {
                        assertThat(r.getOrderId()).isEqualTo(ORDER_ID);
                        assertThat(r.getItems()).hasSize(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void orderNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(orderService.getOrderById(USER_ID, ORDER_ID))
                    .expectError(OrderNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order belongs to a different user")
        void wrongUser() {
            Long otherUserId = 999L;
            when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pendingOrder()));

            StepVerifier.create(orderService.getOrderById(otherUserId, ORDER_ID))
                    .expectError(OrderNotFoundException.class)
                    .verify();

            verify(orderItemRepository, never()).findByOrderId(any());
        }
    }

    // ── getAllOrdersForUser ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllOrdersForUser()")
    class GetAllOrders {

        @Test
        @DisplayName("returns all orders when no status filter")
        void allOrders() {
            Order o1 = pendingOrder();
            Order o2 = orderWithStatus(OrderStatus.PROCESSING);
            o2.setId(11L);

            when(orderRepository.findByUserId(USER_ID)).thenReturn(Flux.just(o1, o2));

            StepVerifier.create(orderService.getAllOrdersForUser(USER_ID, null))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.PENDING))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.PROCESSING))
                    .verifyComplete();
        }

        @Test
        @DisplayName("filters by status when provided")
        void filteredByStatus() {
            when(orderRepository.findByUserIdAndStatus(USER_ID, OrderStatus.PENDING))
                    .thenReturn(Flux.just(pendingOrder()));

            StepVerifier.create(orderService.getAllOrdersForUser(USER_ID, OrderStatus.PENDING))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.PENDING))
                    .verifyComplete();

            verify(orderRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("returns empty flux when user has no orders")
        void noOrders() {
            when(orderRepository.findByUserId(USER_ID)).thenReturn(Flux.empty());

            StepVerifier.create(orderService.getAllOrdersForUser(USER_ID, null))
                    .verifyComplete();
        }
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("cancels PENDING order and returns updated order list")
        void success() {
            Order pending = pendingOrder();
            Order cancelled = orderWithStatus(OrderStatus.CANCELLED);

            when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pending));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(cancelled));
            when(orderRepository.findByUserId(USER_ID)).thenReturn(Flux.just(cancelled));

            StepVerifier.create(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .assertNext(r -> assertThat(r.getStatus()).isEqualTo(OrderStatus.CANCELLED))
                    .verifyComplete();

            verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void orderNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.empty());

            StepVerifier.create(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .expectError(OrderNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order belongs to different user")
        void wrongUser() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Mono.just(pendingOrder()));

            StepVerifier.create(orderService.cancelOrder(999L, ORDER_ID))
                    .expectError(OrderNotFoundException.class)
                    .verify();

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws OrderCancellationException for PROCESSING order")
        void cannotCancelProcessing() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Mono.just(orderWithStatus(OrderStatus.PROCESSING)));

            StepVerifier.create(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .expectError(OrderCancellationException.class)
                    .verify();

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws OrderCancellationException for SHIPPED order")
        void cannotCancelShipped() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Mono.just(orderWithStatus(OrderStatus.SHIPPED)));

            StepVerifier.create(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .expectError(OrderCancellationException.class)
                    .verify();
        }

        @Test
        @DisplayName("throws OrderCancellationException for DELIVERED order")
        void cannotCancelDelivered() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Mono.just(orderWithStatus(OrderStatus.DELIVERED)));

            StepVerifier.create(orderService.cancelOrder(USER_ID, ORDER_ID))
                    .expectError(OrderCancellationException.class)
                    .verify();
        }
    }

    // ── promotePendingOrders ──────────────────────────────────────────────────

    @Nested
    @DisplayName("promotePendingOrders()")
    class PromotePendingOrders {

        @Test
        @DisplayName("promotes all PENDING orders and returns count")
        void promotesOrders() {
            Order p1 = pendingOrder();
            Order p2 = pendingOrder();
            p2.setId(11L);

            when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(Flux.just(p1, p2));
            when(orderRepository.save(any(Order.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(orderService.promotePendingOrders())
                    .assertNext(count -> assertThat(count).isEqualTo(2L))
                    .verifyComplete();

            verify(orderRepository, times(2)).save(argThat(o ->
                    o.getStatus() == OrderStatus.PROCESSING));
        }

        @Test
        @DisplayName("returns zero when no PENDING orders exist")
        void noPendingOrders() {
            when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(Flux.empty());

            StepVerifier.create(orderService.promotePendingOrders())
                    .assertNext(count -> assertThat(count).isEqualTo(0L))
                    .verifyComplete();

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("sets updatedAt timestamp on promoted orders")
        void setsUpdatedAt() {
            Order pending = pendingOrder();
            pending.setUpdatedAt(LocalDateTime.now().minusHours(1));

            when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(Flux.just(pending));
            when(orderRepository.save(any(Order.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(orderService.promotePendingOrders())
                    .expectNextCount(1)
                    .verifyComplete();

            verify(orderRepository).save(argThat(o ->
                    o.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(5))));
        }
    }

}