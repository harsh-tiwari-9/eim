package com.jio.eim.psmo.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
public class PagedLogsResponse {

    private List<OperationLogResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static PagedLogsResponse from(Page<OperationLogResponse> p) {
        PagedLogsResponse r = new PagedLogsResponse();
        r.setContent(p.getContent());
        r.setPage(p.getNumber());
        r.setSize(p.getSize());
        r.setTotalElements(p.getTotalElements());
        r.setTotalPages(p.getTotalPages());
        return r;
    }
}