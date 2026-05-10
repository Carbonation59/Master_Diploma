/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.createDiagrams;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.model.ViewObjects.Views;
import ru.beeline.architecting_graph.repository.neo4j.ContainerRepository;
import ru.beeline.architecting_graph.repository.neo4j.DeploymentNodesRepository;

import java.util.*;

@Service
public class ViewService {


    @Autowired
    ContainerComponentBuilder containerComponentBuilder;

    @Autowired
    SetElements setElements;

    @Autowired
    NodesBuilder nodesBuilder;


    @Autowired
    ContainerRepository containerRepository;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    public Workspace GetContextView(String softwareSystemMnemonic, String rankDirection) {
        DiagramParameters diagramParameters = createNewDiagramParameters(softwareSystemMnemonic);
        createContextView(diagramParameters.getSystem(), softwareSystemMnemonic, diagramParameters);
        updateElements(diagramParameters.getWorkspace(), diagramParameters.getModel(), diagramParameters.getSystem(),
                softwareSystemMnemonic, diagramParameters);
        setElements.setContextView(diagramParameters.getWorkspace(), diagramParameters.getSystem(), diagramParameters
                , rankDirection);
        setElements.setContainerView(diagramParameters.getWorkspace(), diagramParameters.getSystem(),
                                     diagramParameters, rankDirection);
        return diagramParameters.getWorkspace();
    }

    public Workspace GetComponentView(String softwareSystemMnemonic, String containerMnemonic,
                                      String rankDirection) {
        DiagramParameters diagramParameters = createNewDiagramParameters(softwareSystemMnemonic);
        containerComponentBuilder.createComponentView(softwareSystemMnemonic, containerMnemonic, diagramParameters.getSystem(),
                diagramParameters);
        updateElements(diagramParameters.getWorkspace(), diagramParameters.getModel(), diagramParameters.getSystem(),
                softwareSystemMnemonic, diagramParameters);
        setElements.setComponentView(diagramParameters.getWorkspace(), containerMnemonic, diagramParameters, rankDirection);
        return diagramParameters.getWorkspace();
    }

    public Workspace GetDeploymentView(String softwareSystemMnemonic, String environment, String rankDirection) {
        DiagramParameters diagramParameters = createNewDiagramParameters(softwareSystemMnemonic);
        createDeploymentView(softwareSystemMnemonic, environment, diagramParameters.getModel(), diagramParameters);

        updateElements(diagramParameters.getWorkspace(), diagramParameters.getModel(), diagramParameters.getSystem(),
                softwareSystemMnemonic, diagramParameters);

        setElements.setDeploymentView(diagramParameters.getWorkspace(), diagramParameters.getModel(), diagramParameters.getSystem().getId(),
                environment, diagramParameters, rankDirection);
        return diagramParameters.getWorkspace();
    }

    void createDeploymentView(String softwareSystemMnemonic, String environment, Model model,
                              DiagramParameters diagramParameters) {
        Result result = containerRepository.getContainers(softwareSystemMnemonic);
        while (result.hasNext()) {
            Record record = result.next();
            containerComponentBuilder.getContainer(record.get("m").asNode(), diagramParameters);
        }
        result = deploymentNodesRepository.getDeploymentNodes(softwareSystemMnemonic);
        while (result.hasNext()) {
            Record record = result.next();
            nodesBuilder.getDeploymentNode(record.get("m").asNode(), environment, diagramParameters);
        }
        model.setDeploymentNodes(new ArrayList<>());
        result = deploymentNodesRepository.getDeploymentNodes(softwareSystemMnemonic);
        while (result.hasNext()) {
            Record record = result.next();
            String deploymentNodeDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            DeploymentNode deploymentNode = diagramParameters.getCreatedDeploymentNodes().get(deploymentNodeDSLIdentifier);
            if (deploymentNode != null) {
                model.getDeploymentNodes()
                        .add(nodesBuilder.getDeploymentNodeRelationships(deploymentNode, diagramParameters));
            }
        }
    }

    public void createContextView(SoftwareSystem system, String softwareSystemMnemonic, DiagramParameters diagramParameters) {
        containerComponentBuilder.getContainers(softwareSystemMnemonic, diagramParameters);
        containerComponentBuilder.getSystemRelationships(system, softwareSystemMnemonic, diagramParameters);
        containerComponentBuilder.getContainerRelationships(system, softwareSystemMnemonic, true, diagramParameters);

    }

    public DiagramParameters createNewDiagramParameters(String softwareSystemMnemonic) {
        DiagramParameters diagramParameters = new DiagramParameters();
        diagramParameters.setLastObjectId(2L);
        diagramParameters.setObjectMap(new HashMap<>());
        diagramParameters.setParents(new HashMap<>());
        diagramParameters.setSystemChilds(new HashSet<>());
        diagramParameters.setCreatedRelationships(new HashSet<>());
        diagramParameters.setViewObjects(new HashSet<>());
        diagramParameters.setCreatedSystems(new HashMap<>());
        diagramParameters.setCreatedContainers(new HashMap<>());
        diagramParameters.setCreatedComponents(new HashMap<>());
        diagramParameters.setCreatedDeploymentNodes(new HashMap<>());
        diagramParameters.setCreatedInfrastructureNodes(new HashMap<>());
        diagramParameters.setCreatedContainerInstances(new HashMap<>());
        diagramParameters.setWorkspace(new Workspace());
        diagramParameters.setModel(new Model());
        diagramParameters.setSystem(containerComponentBuilder.getSystem(softwareSystemMnemonic, diagramParameters));
        return diagramParameters;
    }

    public void updateElements(Workspace workspace, Model model, SoftwareSystem system, String softwareSystemMnemonic,
                               DiagramParameters diagramParameters) {
        diagramParameters.getCreatedSystems().put(softwareSystemMnemonic, system);
        for (Map.Entry<String, SoftwareSystem> entry : diagramParameters.getCreatedSystems().entrySet()) {
            SoftwareSystem currentSystem = entry.getValue();
            String systemDSLIdentifier = currentSystem.getProperties().get("structurizr_dsl_identifier").toString();
            List<Container> containers = updateSystemContainers(currentSystem, diagramParameters);
            currentSystem.setContainers(containers);
            diagramParameters.getCreatedSystems().put(systemDSLIdentifier, currentSystem);
        }
        system = diagramParameters.getCreatedSystems().get(softwareSystemMnemonic);
        workspace.setId(1L);
        workspace.setViews(new Views());
        model.setSoftwareSystems(new ArrayList<>());
        for (Map.Entry<String, SoftwareSystem> entry : diagramParameters.getCreatedSystems().entrySet()) {
            model.getSoftwareSystems().add(entry.getValue());
        }
        workspace.setModel(model);
    }

    public List<Component> updateContainerComponents(Container container, DiagramParameters diagramParameters) {
        List<Component> components = new ArrayList<>();
        for (Component component : container.getComponents()) {
            String componentDSLIdentifier = component.getProperties().get("structurizr_dsl_identifier").toString();
            components.add(diagramParameters.getCreatedComponents().get(componentDSLIdentifier));
        }
        return components;
    }

    public List<Container> updateSystemContainers(SoftwareSystem system, DiagramParameters diagramParameters) {
        List<Container> containers = new ArrayList<>();
        for (Container container : system.getContainers()) {
            List<Component> components = updateContainerComponents(container, diagramParameters);
            String containerDSLIdentifier = container.getProperties().get("structurizr_dsl_identifier").toString();
            Container updatedContainer = diagramParameters.getCreatedContainers().get(containerDSLIdentifier);
            updatedContainer.setComponents(components);
            diagramParameters.getCreatedContainers().put(containerDSLIdentifier, updatedContainer);
            containers.add(updatedContainer);
        }
        return containers;
    }
}
