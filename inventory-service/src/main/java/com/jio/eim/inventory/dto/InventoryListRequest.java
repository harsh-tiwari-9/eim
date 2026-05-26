package com.jio.eim.inventory.dto;

import jakarta.validation.constraints.Pattern;

public class InventoryListRequest {

    private String ownerId;
    private String status;
    private String search;

    @Pattern(regexp = "\\d{32}", message = "eid must be exactly 32 digits")
    private String eid;

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getEid() {
        return eid;
    }

    public void setEid(String eid) {
        this.eid = eid;
    }
}
