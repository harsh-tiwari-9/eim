package com.jio.eim.inventory.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
@NoArgsConstructor
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PagedResponse<T> from(Page<?> source, List<T> content) {
        PagedResponse<T> response = new PagedResponse<>();
        response.content = content;
        response.page = source.getNumber();
        response.size = source.getSize();
        response.totalElements = source.getTotalElements();
        response.totalPages = source.getTotalPages();
        response.first = source.isFirst();
        response.last = source.isLast();
        return response;
    }
}