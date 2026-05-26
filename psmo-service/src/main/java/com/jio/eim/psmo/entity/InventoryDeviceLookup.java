package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "inventory", name = "devices")
public class InventoryDeviceLookup {

    @Id
    @Column(length = 32)
    private String eid;

    @Column(nullable = false, length = 20)
    private String status;

    public String getEid() { return eid; }
    public void setEid(String eid) { this.eid = eid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}