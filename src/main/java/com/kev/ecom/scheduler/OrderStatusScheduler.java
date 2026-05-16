package com.kev.ecom.scheduler;

import com.kev.ecom.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusScheduler {

    private final OrderService orderService;

    /**
     * Every 5 minutes, promote all PENDING orders to PROCESSING.
     * fixedRate is in milliseconds: 5 * 60 * 1000 = 300_000
     */
    @Scheduled(fixedRate = 300_000)
    public void promotePendingToProcessing() {
        log.info("Scheduler triggered: checking for PENDING orders...");
        orderService.promotePendingOrders()
                .subscribe(
                        count -> log.info("Scheduler complete: {} orders updated", count),
                        error -> log.error("Scheduler error while promoting orders", error)
                );
    }
}

