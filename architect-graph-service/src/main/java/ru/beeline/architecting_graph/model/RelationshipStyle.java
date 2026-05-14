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
public class RelationshipStyle {

    private String tag;
    private Integer thickness;
    private String color;
    private Integer fontSize;
    private Integer width;
    private Boolean dashed;
    private Routing routing;
    private Integer position;
    private Integer opacity;

    public enum Routing {
        Direct,
        Curved,
        Orthogonal
    }
}
