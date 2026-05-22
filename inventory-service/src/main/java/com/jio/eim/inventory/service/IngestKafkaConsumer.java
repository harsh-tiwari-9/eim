package com.jio.eim.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.inventory.ingest.IngestRecordMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IngestKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(IngestKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final IngestRegistrationService registrationService;

    public IngestKafkaConsumer(ObjectMapper objectMapper, IngestRegistrationService registrationService) {
        this.objectMapper = objectMapper;
        this.registrationService = registrationService;
    }

    @KafkaListener(
            topics = "${eim.ingest.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String messageJson) {
        try {
            IngestRecordMessage message = objectMapper.readValue(messageJson, IngestRecordMessage.class);
            registrationService.registerFromKafka(message);
        } catch (Exception ex) {
            log.error("Failed to process ingest Kafka message: {}", messageJson, ex);
        }
    }
}
