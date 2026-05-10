/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model.ViewObjects;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import ru.beeline.architecting_graph.model.AnimationStep;
import ru.beeline.architecting_graph.model.Dimensions;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComponentView {

    private String key;
    private Integer order;
    private String title;
    private String description;
    private Map<String, Object> properties;
    private String containerId;
    private PaperSize paperSize;
    private Dimensions dimensions;
    private AutomaticLayout automaticLayout;
    private List<ElementView> elements;
    private List<RelationshipView> relationships;
    private List<AnimationStep> animations;
    private Boolean externalContainerBoundariesVisible;

    public enum PaperSize {
        A6_Portrait, A6_Landscape,
        A5_Portrait, A5_Landscape,
        A4_Portrait, A4_Landscape,
        A3_Portrait, A3_Landscape,
        A2_Portrait, A2_Landscape,
        A1_Portrait, A1_Landscape,
        A0_Portrait, A0_Landscape,
        Letter_Portrait, Letter_Landscape,
        Legal_Portrait, Legal_Landscape,
        Slide_4_3, Slide_16_9, Slide_16_10
    }
}