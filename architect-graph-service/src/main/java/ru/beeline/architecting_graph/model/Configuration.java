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
public class Configuration {

    private Styles styles;
    private String lastSavedView;
    private String defaultView;
    private List<String> themes;
    private Branding branding;
    private Terminology terminology;
    private MetadataSymbols metadataSymbols;
    private Map<String, Object> properties;

    public enum MetadataSymbols {
        SquareBrackets,
        RoundBrackets,
        CurlyBrackets,
        AngleBrackets,
        DoubleAngleBrackets,
        None
    }
}
