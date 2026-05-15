package com.kev.ecom.repository;

import com.kev.ecom.model.order.OrderItem;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {

    /**
     * Fetch all items linked to an order in their defined position order,
     * by joining through the mapping table — keeps the repository query
     * close to the schema rather than spreading join logic across the service.
     */
    @Query("""
            SELECT i.*
              FROM items i
              JOIN order_item_mappings m ON m.item_id = i.id
             WHERE m.order_id = :orderId
             ORDER BY m.position
            """)
    Flux<OrderItem> findByOrderId(Long orderId);
}

