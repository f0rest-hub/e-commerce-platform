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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemMappingRepository mappingRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Mono<OrderResponse> createOrder(Long userId, CreateOrderRequest request) {
        List<OrderItem> itemsToSave = request.getItems().stream()
                .map(i -> {
                    return OrderItem.builder()
                            .itemId(i.getProductId())
                            .itemName(i.getProductName())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal total = request.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Persist the order header
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return orderRepository.save(order)
                .flatMap(savedOrder ->
                        // 3. Persist each item independently into the items table
                        orderItemRepository.saveAll(itemsToSave)
                                .collectList()
                                .flatMap(savedItems -> {
                                    // 4. Build mapping rows with 1-based position
                                    AtomicInteger pos = new AtomicInteger(1);
                                    List<OrderItemMapping> mappings = savedItems.stream()
                                            .map(item -> OrderItemMapping.builder()
                                                    .orderId(savedOrder.getId())
                                                    .itemId(item.getId())
                                                    .position(pos.getAndIncrement())
                                                    .build())
                                            .collect(Collectors.toList());

                                    // 5. Persist the mapping rows
                                    return mappingRepository.saveAll(mappings)
                                            .collectList()
                                            .map(__ -> {
                                                savedOrder.setItems(savedItems);
                                                return toResponse(savedOrder);
                                            });
                                })
                );
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Mono<OrderResponse> getOrderById(Long userId, Long orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    if (!order.getUserId().equals(userId)) {
                        return Mono.error(new OrderNotFoundException(orderId));
                    }
                    return populateItems(order);
                });
    }

    public Flux<OrderResponse> getOrdersForUser(Long userId, OrderStatus status) {
        Flux<Order> orders = (status != null)
                ? orderRepository.findByUserIdAndStatus(userId, status)
                : orderRepository.findByUserId(userId);

        return orders.flatMap(this::populateItems);
    }

    @Transactional
    public Mono<OrderResponse> cancelOrder(Long userId, Long orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    if (!order.getUserId().equals(userId)) {
                        return Mono.error(new OrderNotFoundException(orderId));
                    }
                    if (order.getStatus() != OrderStatus.PENDING) {
                        return Mono.error(new OrderCancellationException(orderId, order.getStatus().name()));
                    }
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedAt(LocalDateTime.now());
                    return orderRepository.save(order);
                })
                .flatMap(this::populateItems);
    }

    @Transactional
    public Mono<Long> promotePendingOrders() {
        return orderRepository.findByStatus(OrderStatus.PENDING)
                .flatMap(order -> {
                    order.setStatus(OrderStatus.PROCESSING);
                    order.setUpdatedAt(LocalDateTime.now());
                    return orderRepository.save(order);
                })
                .count()
                .doOnNext(count -> log.info("Scheduler: promoted {} PENDING → PROCESSING", count));
    }

    /**
     * Loads items for an order via the mapping join and attaches them
     * to the order before mapping to the response DTO.
     */
    private Mono<OrderResponse> populateItems(Order order) {
        return orderItemRepository.findByOrderId(order.getId())
                .collectList()
                .map(items -> {
                    order.setItems(items);
                    return toResponse(order);
                });
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems() == null
                ? List.of()
                : order.getItems().stream()
                  .map(i -> OrderResponse.OrderItemResponse.builder()
                            .id(i.getId())
                            .itemId(i.getItemId())
                            .itemName(i.getItemName())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .subtotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                            .build())
                  .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}
