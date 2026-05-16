package com.kev.ecom.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Request object for creating a new order")
public class CreateOrderRequest {
    @NotEmpty(message = "Order must have at least one item")
    @Valid
    @Schema(description = "List of items to include in the order")
    private List<OrderItemRequest> items;

    @Data
    @Schema(description = "Details of an item to be added to the order")
    public static class OrderItemRequest {

        @NotBlank(message = "Item ID is required")
        @JsonProperty("item_id")
        @Schema(description = "External ID or SKU of the item", example = "PRD-001")
        private String itemId;

        @NotBlank(message = "Item name is required")
        @JsonProperty("item_name")
        @Schema(description = "Name of the item", example = "Wireless Mouse")
        private String itemName;

        @Min(value = 1, message = "Quantity must be at least 1")
        @Schema(description = "Quantity of the item to order", example = "1")
        private Integer quantity;

        @Digits(integer = 10, fraction = 2, message = "Unit price must be a valid monetary amount")
        @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
        @JsonProperty("unit_price")
        @Schema(description = "Price per unit of the item", example = "25.00")
        private BigDecimal unitPrice;
    }
}
