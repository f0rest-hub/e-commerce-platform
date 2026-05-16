package com.kev.ecom.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {

    // userId is NOT in the request body — it is resolved from the JWT in the service layer

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {

        @NotBlank(message = "Item ID is required")
        @JsonProperty("item_id")
        private String itemId;

        @NotBlank(message = "Item name is required")
        @JsonProperty("item_name")
        private String itemName;

        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
        @JsonProperty("unit_price")
        private BigDecimal unitPrice;
    }
}
