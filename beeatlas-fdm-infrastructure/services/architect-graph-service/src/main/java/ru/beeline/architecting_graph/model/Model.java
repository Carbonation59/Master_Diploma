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
public class Model {

    private Enterprise enterprise;
    private List<Person> people;
    private List<SoftwareSystem> softwareSystems;
    private List<DeploymentNode> deploymentNodes;
    private Map<String, Object> properties;
}