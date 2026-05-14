/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.beeline.architecting_graph.model.ViewObjects.Views;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Workspace {

    private Long id;
    private String name;
    private String description;
    private String version;
    private String thumbnail;
    private String lastModifiedDate;
    private String lastModifiedUser;
    private String lastModifiedAgent;
    private Model model;
    private Views views;
    private Documentation documentation;
    private WorkspaceConfiguration configuration;
    private Map<String, Object> properties;
}