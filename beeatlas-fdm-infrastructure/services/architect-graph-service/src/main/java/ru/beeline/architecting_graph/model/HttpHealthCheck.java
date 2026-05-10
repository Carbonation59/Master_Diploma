/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HttpHealthCheck {

    private String name;
    private String url;
    private Integer interval;
    private Integer timeout;
    private Map<String, String> headers;
}
