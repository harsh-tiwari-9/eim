package com.jio.eim.inventory.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public PagedResponse() {
    }

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

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
