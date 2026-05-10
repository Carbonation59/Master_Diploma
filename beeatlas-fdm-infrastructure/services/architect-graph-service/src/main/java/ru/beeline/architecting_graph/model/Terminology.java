/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Terminology {

    private String enterprise;
    private String person;
    private String softwareSystem;
    private String container;
    private String component;
    private String code;
    private String deploymentNode;
    private String relationship;
}
