/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiagramParameters {

    private Long lastObjectId;
    private Map<String, Long> objectMap;
    private Map<String, String> parents;
    private Set<String> systemChilds;
    private Set<Edge> createdRelationships;
    private Set<String> viewObjects;
    private Map<String, SoftwareSystem> createdSystems;
    private Map<String, Container> createdContainers;
    private Map<String, Component> createdComponents;
    private Map<String, DeploymentNode> createdDeploymentNodes;
    private Map<String, InfrastructureNode> createdInfrastructureNodes;
    private Map<String, ContainerInstance> createdContainerInstances;
    private Workspace workspace;
    private Model model;
    private SoftwareSystem system;

}
