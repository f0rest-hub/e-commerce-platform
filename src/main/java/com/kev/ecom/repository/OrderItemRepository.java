package com.kev.ecom.repository;

import com.kev.ecom.model.order.OrderItem;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {

    /**
     * Fetch all items linked to an order
     */
    @Query("""
            SELECT i.*
              FROM items i
              JOIN order_item_mappings m ON m.item_id = i.id
             WHERE m.order_id = :orderId
            """)
    Flux<OrderItem> findByOrderId(Long orderId);
}

