package com.jio.eim.psmo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.psmo.dto.PsmoCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PsmoCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(PsmoCommandConsumer.class);

    private final ObjectMapper objectMapper;
    private final PackageSigningService signingService;

    public PsmoCommandConsumer(ObjectMapper objectMapper, PackageSigningService signingService) {
        this.objectMapper = objectMapper;
        this.signingService = signingService;
    }

    @KafkaListener(
            topics = "${eim.psmo.kafka.commands-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String json) {
        try {
            PsmoCommandMessage message = objectMapper.readValue(json, PsmoCommandMessage.class);
            signingService.process(message);
        } catch (Exception ex) {
            log.error("Failed to consume PSMO command message: {}", json, ex);
        }
    }


}