package com.kev.ecom.repository;


import com.kev.ecom.model.order.OrderItemMapping;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderItemMappingRepository extends ReactiveCrudRepository<OrderItemMapping, Long> {
    /** Delete all mappings for an order (used on cancellation / deletion). */
    //TODO - Maybe cleanup tables when order cancelled
    Mono<Void> deleteByOrderId(Long orderId);

}