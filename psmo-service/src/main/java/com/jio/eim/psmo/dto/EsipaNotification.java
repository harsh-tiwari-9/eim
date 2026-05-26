package com.jio.eim.psmo.dto;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsipaNotification {

    private String eid;
    private Long opId;
    private String status;
    private Map<String, Object> result;
}