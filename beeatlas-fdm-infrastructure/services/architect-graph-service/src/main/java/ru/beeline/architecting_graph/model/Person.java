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
public class Person {

    private String id;
    private String name;
    private String description;
    private String tags;
    private String url;
    private Location location;
    private String group;
    private Map<String, Object> properties;
    private List<Perspective> perspectives;
    private List<RelationshipEntity> relationships;

    public enum Location {
        External,
        Internal,
        Unspecified
    }
}