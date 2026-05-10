/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RelationshipEntity {

    private String id;
    private String description;
    private String tags;
    private String url;
    private Map<String, Object> properties;
    private List<Perspective> perspectives;
    private String sourceId;
    private String destinationId;
    private String technology;
    private InteractionStyle interactionStyle;
    private String linkedRelationshipId;

    public enum InteractionStyle {
        Synchronous,
        Asynchronous
    }
}