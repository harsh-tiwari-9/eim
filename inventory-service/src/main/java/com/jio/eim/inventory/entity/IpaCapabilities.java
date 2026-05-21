package com.jio.eim.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "inventory", name = "ipa_capabilities")
public class IpaCapabilities {

    @Id
    @Column(length = 32)
    private String eid;

    @Column(name = "direct_rsp_server_communication", nullable = false)
    private boolean directRspServerCommunication;

    @Column(name = "indirect_rsp_server_communication", nullable = false)
    private boolean indirectRspServerCommunication;

    public String getEid() {
        return eid;
    }

    public void setEid(String eid) {
        this.eid = eid;
    }

    public boolean isDirectRspServerCommunication() {
        return directRspServerCommunication;
    }

    public void setDirectRspServerCommunication(boolean directRspServerCommunication) {
        this.directRspServerCommunication = directRspServerCommunication;
    }

    public boolean isIndirectRspServerCommunication() {
        return indirectRspServerCommunication;
    }

    public void setIndirectRspServerCommunication(boolean indirectRspServerCommunication) {
        this.indirectRspServerCommunication = indirectRspServerCommunication;
    }
}
