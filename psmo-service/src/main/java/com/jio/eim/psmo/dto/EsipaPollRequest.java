package com.jio.eim.psmo.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsipaPollRequest {

    private String eid;
    private List<EsipaNotification> lastResults;
}