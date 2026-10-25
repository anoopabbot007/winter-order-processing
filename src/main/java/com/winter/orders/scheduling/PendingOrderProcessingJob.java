package com.winter.orders.scheduling;

import com.winter.orders.application.PendingOrderProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingOrderProcessingJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderProcessingJob.class);

    private final PendingOrderProcessor processor;
    private final int batchSize;

    public PendingOrderProcessingJob(
            PendingOrderProcessor processor,
            @Value("${orders.processing.batch-size:100}") int batchSize) {
        this.processor = processor;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${orders.processing.cron:0 */5 * * * *}", zone = "${orders.processing.zone:UTC}")
    public void processPendingOrders() {
        try {
            int processed = processor.processNextBatch(batchSize);
            log.info("Pending order processing batch completed; processed={}", processed);
        } catch (RuntimeException exception) {
            log.error("Pending order processing batch failed", exception);
        }
    }
}
