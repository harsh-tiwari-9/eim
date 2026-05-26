package com.jio.eim.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "inventory", name = "device_profiles")
@Getter
@Setter
public class DeviceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String eid;

    @Column(nullable = false, length = 20)
    private String iccid;

    @Column(nullable = false, length = 20)
    private String state;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "profile_class", length = 1)
    private String profileClass;

    @Column(name = "mno_id", length = 50)
    private String mnoId;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;
}