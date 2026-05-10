/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model.ViewObjects;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import ru.beeline.architecting_graph.model.Configuration;
import ru.beeline.architecting_graph.model.DynamicView;
import ru.beeline.architecting_graph.model.FilteredView;
import ru.beeline.architecting_graph.model.ImageView;
import ru.beeline.architecting_graph.model.SystemLandscapeView;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Views {

    private List<SystemLandscapeView> systemLandscapeViews;
    private List<SystemContextView> systemContextViews;
    private List<ContainerView> containerViews;
    private List<ComponentView> componentViews;
    private List<DynamicView> dynamicViews;
    private List<DeploymentView> deploymentViews;
    private List<FilteredView> filteredViews;
    private List<ImageView> imageViews;
    private Configuration configuration;

}
