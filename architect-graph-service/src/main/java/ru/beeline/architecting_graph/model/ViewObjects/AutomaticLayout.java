/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model.ViewObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutomaticLayout {

    private LayoutImplementation implementation;
    private RankDirection rankDirection;
    private Integer rankSeparation;
    private Integer nodeSeparation;
    private Integer edgeSeparation;
    private Boolean vertices;
    private Boolean applied;

    public enum LayoutImplementation {
        Graphviz,
        Dagre
    }

    public enum RankDirection {
        TopBottom,
        BottomTop,
        LeftRight,
        RightLeft
    }
}