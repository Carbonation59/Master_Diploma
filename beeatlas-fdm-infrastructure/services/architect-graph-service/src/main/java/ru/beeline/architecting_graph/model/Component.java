/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Component {

    private String id;
    private String name;
    private String description;
    private String technology;
    private String originalName;
    private String tags;
    private String url;
    private String group;
    private Map<String, Object> properties;
    private List<Perspective> perspectives;
    private List<RelationshipEntity> relationships;
    private Documentation documentation;
}