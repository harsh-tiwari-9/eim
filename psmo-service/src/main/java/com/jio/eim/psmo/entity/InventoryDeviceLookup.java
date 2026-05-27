package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "inventory", name = "devices")
@Getter
@Setter
public class InventoryDeviceLookup {

    @Id
    @Column(length = 32)
    private String eid;

    @Column(nullable = false, length = 20)
    private String status;
}