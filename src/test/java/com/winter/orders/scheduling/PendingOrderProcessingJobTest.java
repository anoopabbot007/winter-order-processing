package com.winter.orders.scheduling;

import static org.mockito.Mockito.verify;

import com.winter.orders.application.PendingOrderProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingOrderProcessingJobTest {

    @Mock
    private PendingOrderProcessor processor;

    @Test
    void canInvokeTheScheduledEntryPointDirectly() {
        var job = new PendingOrderProcessingJob(processor, 25);

        job.processPendingOrders();

        verify(processor).processNextBatch(25);
    }
}
