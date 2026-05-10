/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.createDiagrams;

import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.model.ViewObjects.*;
import ru.beeline.architecting_graph.model.ViewObjects.AutomaticLayout.LayoutImplementation;
import ru.beeline.architecting_graph.model.ViewObjects.AutomaticLayout.RankDirection;

import java.util.*;

@Service
public class SetElements {

    ElementView elementView = new ElementView();
    RelationshipView relationshipView = new RelationshipView();

    public AutomaticLayout createAutomaticLayout(String rankDirection) {
        AutomaticLayout automaticLayout = new AutomaticLayout();
        automaticLayout.setApplied(false);
        automaticLayout.setEdgeSeparation(0);
        automaticLayout.setImplementation(LayoutImplementation.Graphviz);
        automaticLayout.setNodeSeparation(300);
        automaticLayout.setRankDirection(rankDirection == null ? RankDirection.TopBottom : RankDirection.valueOf(rankDirection));
        automaticLayout.setRankSeparation(300);
        automaticLayout.setVertices(false);
        return automaticLayout;
    }

    public void setContextViewRelationships(String systemId, RelationshipEntity relationship, SystemContextView systemContextView,
                                            DiagramParameters diagramParameters) {
        if (systemId.equals(relationship.getSourceId()) || systemId.equals(relationship.getDestinationId())) {
            RelationshipView contextRelationshipView = relationshipView.createWithId(relationship.getId());
            systemContextView.getRelationships().add(contextRelationshipView);
            String otherId = systemId.equals(relationship.getSourceId())
                    ? relationship.getDestinationId()
                    : relationship.getSourceId();
            if (!diagramParameters.getViewObjects().contains(otherId)) {
                ElementView elementViewToAdd = elementView.createWithId(otherId);
                systemContextView.getElements().add(elementViewToAdd);
                diagramParameters.getViewObjects().add(otherId);
            }
        }
    }

    public void setContextView(Workspace workspace, SoftwareSystem system, DiagramParameters diagramParameters,
                               String rankDirection) {
        List<SystemContextView> systemContextViews = new ArrayList<>();
        SystemContextView systemContextView = new SystemContextView();
        systemContextView.setElements(new ArrayList<>());
        systemContextView.setRelationships(new ArrayList<>());
        systemContextView.setSoftwareSystemId(system.getId());
        systemContextView.setEnterpriseBoundaryVisible(true);
        systemContextView.setKey("context");
        systemContextView.setOrder(1);
        AutomaticLayout automaticLayout = createAutomaticLayout(rankDirection);
        systemContextView.setAutomaticLayout(automaticLayout);
        for (Map.Entry<String, SoftwareSystem> entry : diagramParameters.getCreatedSystems().entrySet()) {
            SoftwareSystem currentSystem = entry.getValue();
            if (currentSystem.getId().equals(system.getId())) {
                ElementView systemElementView = elementView.createWithId(system.getId());
                systemContextView.getElements().add(systemElementView);
                diagramParameters.getViewObjects().add(system.getId());
            }
            for (RelationshipEntity relationship : currentSystem.getRelationships()) {
                setContextViewRelationships(system.getId(), relationship, systemContextView, diagramParameters);
            }
        }
        systemContextViews.add(systemContextView);
        workspace.getViews().setSystemContextViews(systemContextViews);
    }

    public void setContainerViewDirectRelationships(ContainerView containerView, RelationshipEntity relationship,
                                                    DiagramParameters diagramParameters) {
        RelationshipView containerRelationshipView = relationshipView.createWithId(relationship.getId());
        containerView.getRelationships().add(containerRelationshipView);
        if (!diagramParameters.getViewObjects().contains(relationship.getDestinationId())) {
            ElementView destinationElementView = elementView.createWithId(relationship.getDestinationId());
            containerView.getElements().add(destinationElementView);
            diagramParameters.getViewObjects().add(relationship.getDestinationId());
        }
    }

    public void setContainerViewElements(SoftwareSystem system, ContainerView containerView, Set<String> containersId,
                                         DiagramParameters diagramParameters) {
        for (Container container : system.getContainers()) {
            ElementView containerElementView = elementView.createWithId(container.getId());
            if (!diagramParameters.getViewObjects().contains(container.getId())) {
                containerView.getElements().add(containerElementView);
                diagramParameters.getViewObjects().add(container.getId());
            }
            containersId.add(container.getId());
            for (RelationshipEntity relationship : container.getRelationships()) {
                setContainerViewDirectRelationships(containerView, relationship, diagramParameters);
            }
        }

    }

    public void setContainerViewReverseRelationships(ContainerView containerView, Set<String> containersId,
                                                     DiagramParameters diagramParameters) {
        for (Map.Entry<String, SoftwareSystem> entry : diagramParameters.getCreatedSystems().entrySet()) {
            SoftwareSystem currentSystem = entry.getValue();
            for (RelationshipEntity relationship : currentSystem.getRelationships()) {
                if (containersId.contains(relationship.getDestinationId())) {
                    RelationshipView containerRelationshipView = relationshipView.createWithId(relationship.getId());
                    containerView.getRelationships().add(containerRelationshipView);
                    if (!diagramParameters.getViewObjects().contains(relationship.getSourceId())) {
                        ElementView sourceElementView = elementView.createWithId(relationship.getSourceId());
                        containerView.getElements().add(sourceElementView);
                        diagramParameters.getViewObjects().add(relationship.getSourceId());
                    }
                }
            }
        }
    }

    public void setContainerView(Workspace workspace, SoftwareSystem system, DiagramParameters diagramParameters,
                                 String rankDirection) {
        diagramParameters.setViewObjects(new HashSet<>());
        List<ContainerView> containerViews = new ArrayList<>();
        ContainerView containerView = new ContainerView();
        containerView.setElements(new ArrayList<>());
        containerView.setRelationships(new ArrayList<>());
        containerView.setSoftwareSystemId(system.getId());
        containerView.setExternalSoftwareSystemBoundariesVisible(false);
        containerView.setKey("containers");
        containerView.setOrder(2);
        AutomaticLayout automaticLayout = createAutomaticLayout(rankDirection);
        containerView.setAutomaticLayout(automaticLayout);
        diagramParameters.getViewObjects().add(system.getId());
        Set<String> containersId = new HashSet<>();
        setContainerViewElements(system, containerView, containersId, diagramParameters);
        setContainerViewReverseRelationships(containerView, containersId, diagramParameters);
        containerViews.add(containerView);
        workspace.getViews().setContainerViews(containerViews);
    }

    public void setComponentViewDirectRelationships(ComponentView componentView, RelationshipEntity relationship,
                                                    DiagramParameters diagramParameters) {
        RelationshipView componentRelationshipsView = relationshipView.createWithId(relationship.getId());
        componentView.getRelationships().add(componentRelationshipsView);
        if (!diagramParameters.getViewObjects().contains(relationship.getDestinationId())) {
            ElementView destinationElementView = elementView.createWithId(relationship.getDestinationId());
            componentView.getElements().add(destinationElementView);
            diagramParameters.getViewObjects().add(relationship.getDestinationId());
        }
    }

    public void setComponentViewElements(Container container, Set<String> componentsId, ComponentView componentView,
                                         DiagramParameters diagramParameters) {
        for (Component component : container.getComponents()) {
            ElementView componentElementView = elementView.createWithId(component.getId());
            if (!diagramParameters.getViewObjects().contains(component.getId())) {
                componentView.getElements().add(componentElementView);
                diagramParameters.getViewObjects().add(component.getId());
            }
            componentsId.add(component.getId());
            for (RelationshipEntity relationship : component.getRelationships()) {
                setComponentViewDirectRelationships(componentView, relationship, diagramParameters);
            }
        }
    }

    public void setComponentViewReverseRelationships(Set<String> componentsId, ComponentView componentView,
                                                     DiagramParameters diagramParameters) {
        for (Map.Entry<String, SoftwareSystem> entry : diagramParameters.getCreatedSystems().entrySet()) {
            SoftwareSystem currentSystem = entry.getValue();
            for (RelationshipEntity relationship : currentSystem.getRelationships()) {
                if (componentsId.contains(relationship.getDestinationId())) {
                    RelationshipView componentRelationshipView = relationshipView.createWithId(relationship.getId());
                    componentView.getRelationships().add(componentRelationshipView);
                    if (!diagramParameters.getViewObjects().contains(relationship.getSourceId())) {
                        ElementView sourceElementView = elementView.createWithId(relationship.getSourceId());
                        componentView.getElements().add(sourceElementView);
                        diagramParameters.getViewObjects().add(relationship.getSourceId());
                    }
                }
            }
        }
    }

    public void setComponentView(Workspace workspace, String containerMnemonic, DiagramParameters diagramParameters,
                                 String rankDirection) {
        List<ComponentView> componentViews = new ArrayList<>();
        ComponentView componentView = new ComponentView();
        componentView.setElements(new ArrayList<>());
        componentView.setRelationships(new ArrayList<>());
        Container container = diagramParameters.getCreatedContainers().get(containerMnemonic);
        componentView.setContainerId(container.getId());
        componentView.setExternalContainerBoundariesVisible(true);
        componentView.setKey("components");
        componentView.setOrder(3);
        AutomaticLayout automaticLayout = createAutomaticLayout(rankDirection);
        componentView.setAutomaticLayout(automaticLayout);
        Set<String> componentsId = new HashSet<>();
        setComponentViewElements(container, componentsId, componentView, diagramParameters);
        setComponentViewReverseRelationships(componentsId, componentView, diagramParameters);
        componentViews.add(componentView);
        workspace.getViews().setComponentViews(componentViews);
    }

    public void setDeploymentViewRealtionships(RelationshipEntity relationship, DeploymentView deploymentView,
                                               DiagramParameters diagramParameters) {
        if (!diagramParameters.getViewObjects().contains(relationship.getId())) {
            RelationshipView elementRelationshipView = relationshipView.createWithId(relationship.getId());
            deploymentView.getRelationships().add(elementRelationshipView);
            diagramParameters.getViewObjects().add(elementRelationshipView.getId());
        }
        if (!diagramParameters.getViewObjects().contains(relationship.getDestinationId())) {
            ElementView destinationElementView = elementView.createWithId(relationship.getDestinationId());
            deploymentView.getElements().add(destinationElementView);
            diagramParameters.getViewObjects().add(destinationElementView.getId());
        }
    }

    public void setDeploymentNodeElement(DeploymentNode deploymentNode, DeploymentView deploymentView,
                                         DiagramParameters diagramParameters) {
        if (!diagramParameters.getViewObjects().contains(deploymentNode.getId())) {
            ElementView deploymentNodeElementView = elementView.createWithId(deploymentNode.getId());
            deploymentView.getElements().add(deploymentNodeElementView);
            diagramParameters.getViewObjects().add(deploymentNodeElementView.getId());
        }
        for (RelationshipEntity relationship : deploymentNode.getRelationships()) {
            setDeploymentViewRealtionships(relationship, deploymentView, diagramParameters);
        }
    }

    public void setInfrastructureNodeElements(DeploymentNode deploymentNode, DeploymentView deploymentView,
                                              DiagramParameters diagramParameters) {
        for (InfrastructureNode infrastructureNode : deploymentNode.getInfrastructureNodes()) {
            if (!diagramParameters.getViewObjects().contains(infrastructureNode.getId())) {
                ElementView infrastructureNodeElementView = elementView.createWithId(infrastructureNode.getId());
                deploymentView.getElements().add(infrastructureNodeElementView);
                diagramParameters.getViewObjects().add(infrastructureNodeElementView.getId());
            }
            for (RelationshipEntity relationship : infrastructureNode.getRelationships()) {
                setDeploymentViewRealtionships(relationship, deploymentView, diagramParameters);
            }
        }
    }

    public void setContainerInstanceElements(DeploymentNode deploymentNode, DeploymentView deploymentView,
                                             DiagramParameters diagramParameters) {
        for (ContainerInstance containerInstance : deploymentNode.getContainerInstances()) {
            if (!diagramParameters.getViewObjects().contains(containerInstance.getId())) {
                ElementView containerInstanceElementView = elementView.createWithId(containerInstance.getId());
                deploymentView.getElements().add(containerInstanceElementView);
                diagramParameters.getViewObjects().add(containerInstanceElementView.getId());
            }
            for (RelationshipEntity relationship : containerInstance.getRelationships()) {
                setDeploymentViewRealtionships(relationship, deploymentView, diagramParameters);
            }
        }
    }

    public void setDeploymentViewNode(DeploymentNode deploymentNode, DeploymentView deploymentView,
                                      DiagramParameters diagramParameters) {
        setDeploymentNodeElement(deploymentNode, deploymentView, diagramParameters);
        setInfrastructureNodeElements(deploymentNode, deploymentView, diagramParameters);
        setContainerInstanceElements(deploymentNode, deploymentView, diagramParameters);
        for (DeploymentNode childDeploymentNode : deploymentNode.getChildren()) {
            setDeploymentViewNode(childDeploymentNode, deploymentView, diagramParameters);
        }
    }

    public void setDeploymentView(Workspace workspace, Model model, String systemId, String environment,
                                  DiagramParameters diagramParameters, String rankDirection) {
        List<DeploymentView> deploymentViews = new ArrayList<>();
        DeploymentView deploymentView = new DeploymentView();
        deploymentView.setElements(new ArrayList<>());
        deploymentView.setRelationships(new ArrayList<>());
        deploymentView.setSoftwareSystemId(systemId);
        deploymentView.setTitle("Диаграмма развёртывания");
        deploymentView.setEnvironment(environment);
        deploymentView.setKey(environment + "-01");
        deploymentView.setOrder(4);
        AutomaticLayout automaticLayout = createAutomaticLayout(rankDirection);
        deploymentView.setAutomaticLayout(automaticLayout);
        for (DeploymentNode deploymentNode : model.getDeploymentNodes()) {
            setDeploymentViewNode(deploymentNode, deploymentView, diagramParameters);
        }
        deploymentViews.add(deploymentView);
        workspace.getViews().setDeploymentViews(deploymentViews);
    }
}
