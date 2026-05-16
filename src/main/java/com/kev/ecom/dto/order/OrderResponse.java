package com.kev.ecom.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kev.ecom.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object containing order details")
public class OrderResponse {
    @JsonProperty("order_id")
    @Schema(description = "Unique ID of the order", example = "101")
    private Long orderId;

    @JsonProperty("user_id")
    @Schema(description = "ID of the user who placed the order", example = "1")
    private Long userId;

    @Schema(description = "Current status of the order", example = "PENDING")
    private OrderStatus status;

    @JsonProperty("total_amount")
    @Schema(description = "Total amount for the order", example = "150.00")
    private BigDecimal totalAmount;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the order was created")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when the order was last updated")
    private LocalDateTime updatedAt;

    @Schema(description = "List of items in the order")
    private List<OrderItemResponse> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Details of an individual item in an order")
    public static class OrderItemResponse {
        @JsonProperty("item_id")
        @Schema(description = "External ID or SKU of the item", example = "PRD-001")
        private String itemId;

        @JsonProperty("item_name")
        @Schema(description = "Name of the item", example = "Wireless Mouse")
        private String itemName;

        @Schema(description = "Quantity ordered", example = "2")
        private Integer quantity;

        @JsonProperty("item_price")
        @Schema(description = "Price per unit of the item", example = "25.00")
        private BigDecimal unitPrice;

        @Schema(description = "Subtotal for this item (price * quantity)", example = "50.00")
        private BigDecimal subtotal;
    }
}
