/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model.ViewObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import ru.beeline.architecting_graph.model.Vertex;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationshipView {

    private String id;
    private String description;
    private Boolean response;
    private String order;
    private List<Vertex> vertices;
    private Routing routing;
    private Integer position;

    public enum Routing {
        Direct,
        Curved,
        Orthogonal
    }

    public RelationshipView createWithId(String id) {
        RelationshipView relationship = new RelationshipView();
        relationship.setId(id);
        return relationship;
    }
}
