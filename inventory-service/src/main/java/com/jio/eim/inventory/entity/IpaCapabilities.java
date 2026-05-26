package com.jio.eim.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "inventory", name = "ipa_capabilities")
@Getter
@Setter
public class IpaCapabilities {

    @Id
    @Column(length = 32)
    private String eid;

    @Column(name = "direct_rsp_server_communication", nullable = false)
    private boolean directRspServerCommunication;

    @Column(name = "indirect_rsp_server_communication", nullable = false)
    private boolean indirectRspServerCommunication;
}