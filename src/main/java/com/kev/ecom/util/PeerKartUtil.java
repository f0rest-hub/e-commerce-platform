package com.kev.ecom.util;

import com.kev.ecom.enums.OrderStatus;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ServerWebInputException;

import java.util.Arrays;

@UtilityClass
public class PeerKartUtil {
    public static @Nullable String getWebFluxErrorMessage(ServerWebInputException ex) {
        String message = ex.getReason();
        Throwable cause = ex.getCause();

        while (cause != null) {
            /** Enum mismatch handling */
            if (cause instanceof IllegalArgumentException illegalArgEx) {
                if (illegalArgEx.getMessage() != null && illegalArgEx.getMessage().contains("No enum constant")) {
                    return "Invalid order status. Allowed values: " + Arrays.toString(OrderStatus.values());
                }
            }
            cause = cause.getCause();
        }

        return message;
    }
}
