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
public class DeploymentNode {

    private String id;
    private String name;
    private String originalName;
    private String description;
    private String technology;
    private String environment;
    private String instances;
    private String tags;
    private String url;
    private List<DeploymentNode> children;
    private List<InfrastructureNode> infrastructureNodes;
    private List<SoftwareSystemInstance> softwareSystemInstances;
    private List<ContainerInstance> containerInstances;
    private Map<String, Object> properties;
    private List<Perspective> perspectives;
    private List<RelationshipEntity> relationships;
}
