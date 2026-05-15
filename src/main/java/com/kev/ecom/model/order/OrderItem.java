package com.kev.ecom.model.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("items")
public class OrderItem {

    @Id
    private Long id;

    @Column("item_id")
    private String itemId;

    @Column("item_name")
    private String itemName;

    private Integer quantity;

    @Column("unit_price")
    private BigDecimal unitPrice;
}

