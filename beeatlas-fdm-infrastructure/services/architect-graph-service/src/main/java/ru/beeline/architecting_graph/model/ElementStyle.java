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
public class ElementStyle {

    private String tag;
    private Integer width;
    private Integer height;
    private String background;
    private String stroke;
    private Integer strokeWidth;
    private String color;
    private Integer fontSize;
    private Shape shape;
    private String icon;
    private Border border;
    private Integer opacity;
    private Boolean metadata;
    private Boolean description;

    public enum Shape {
        Box,
        RoundedBox,
        Component,
        Circle,
        Ellipse,
        Hexagon,
        Diamond,
        Folder,
        Cylinder,
        Pipe,
        WebBrowser,
        Window,
        MobileDevicePortrait,
        MobileDeviceLandscape,
        Person,
        Robot
    }

    public enum Border {
        Solid,
        Dashed,
        Dotted
    }
}
