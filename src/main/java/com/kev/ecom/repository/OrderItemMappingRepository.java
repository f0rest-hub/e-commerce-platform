package com.kev.ecom.repository;


import com.kev.ecom.model.order.OrderItemMapping;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderItemMappingRepository extends ReactiveCrudRepository<OrderItemMapping, Long> {

    /** All mapping rows for a given order, ordered by position. */
    Flux<OrderItemMapping> findByOrderIdOrderByPosition(Long orderId);

    /** Delete all mappings for an order (used on cancellation / deletion). */
    Mono<Void> deleteByOrderId(Long orderId);

    /** Count how many items are linked to an order. */
    Mono<Long> countByOrderId(Long orderId);

    /**
     * Fetch the item IDs linked to an order in position order.
     * Used to drive the subsequent items lookup.
     */
    @Query("SELECT item_id FROM order_item_mappings WHERE order_id = :orderId ORDER BY position")
    Flux<Long> findItemIdsByOrderId(Long orderId);
}