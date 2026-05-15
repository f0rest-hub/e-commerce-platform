package com.kev.ecom.repository;

import com.kev.ecom.enums.OrderStatus;
import com.kev.ecom.model.order.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    Flux<Order> findByStatus(OrderStatus status);

    Flux<Order> findByUserId(Long userId);

    Flux<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
}

