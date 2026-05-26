package com.jio.eim.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "inventory", name = "devices")
@Getter
@Setter
public class InventoryDevice {

    @Id
    @Column(length = 32)
    private String eid;

    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "auto_enable", nullable = false)
    private boolean autoEnable;

    @Column(name = "auto_delete", nullable = false)
    private boolean autoDelete;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}