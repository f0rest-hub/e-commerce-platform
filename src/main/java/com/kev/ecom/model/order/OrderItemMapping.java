package com.kev.ecom.model.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order_item_mappings")
public class OrderItemMapping {
    @Id
    private Long id;

    @Column("order_id")
    private Long orderId;

    @Column("item_id")
    private Long itemId;
}