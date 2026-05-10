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
public class SoftwareSystem {

    private String id;
    private String name;
    private String description;
    private Location location;
    private String tags;
    private String url;
    private String cmdb;
    private List<Container> containers;
    private String group;
    private Map<String, Object> properties;
    private List<Perspective> perspectives;
    private List<RelationshipEntity> relationships;
    private Documentation documentation;

    public enum Location {
        External,
        Internal,
        Unspecified
    }
}
