/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SoftwareSystemInstance {

    private String id;
    private String softwareSystemId;
    private Integer instanceId;
    private String environment;
    private String tags;
    private Map<String, Object> properties;
    private List<Perspective> perspectives;
    private List<RelationshipEntity> relationships;
    private List<HttpHealthCheck> healthChecks;

}
