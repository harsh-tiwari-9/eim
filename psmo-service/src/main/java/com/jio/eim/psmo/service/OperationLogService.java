package com.jio.eim.psmo.service;

import com.jio.eim.psmo.dto.OperationLogResponse;
import com.jio.eim.psmo.dto.PagedLogsResponse;
import com.jio.eim.psmo.repository.OperationLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLogService {

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    // Logs of a single operation, enriched with operation context
    @Transactional(readOnly = true)
    public List<OperationLogResponse> getForOperation(Long operationId) {
        return repository.findEnrichedByOperationId(operationId);
    }

    // Paginated, filterable feed of all logs (enriched + sort/page via Pageable)
    @Transactional(readOnly = true)
    public PagedLogsResponse search(
            Long operationId,
            String eventType,
            String actor,
            Instant from,
            Instant to,
            Pageable pageable) {

        Page<OperationLogResponse> page = repository.searchWithContext(
                operationId,
                (eventType == null || eventType.isBlank()) ? null : eventType,
                (actor == null || actor.isBlank()) ? null : actor,
                from,
                to,
                pageable);

        return PagedLogsResponse.from(page);
    }
}