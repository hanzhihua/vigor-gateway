package com.unknown.vigor.gateway.core.sink;

import java.util.List;

public class SenderGroup {
    private List<AdvancedKafkaProducer> producers;
    private List<SenderTask> senders;

    public SenderGroup(List<AdvancedKafkaProducer> producers, List<SenderTask> senders) {
        this.producers = producers;
        this.senders = senders;
    }

    public void stop() {
        senders.forEach(t->t.stop());
        producers.forEach(p->p.close());
    }
}
