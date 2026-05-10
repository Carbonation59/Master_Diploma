/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.createDiagrams;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.dto.ContainerNodeDTO;
import ru.beeline.architecting_graph.exception.ValidationException;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.repository.neo4j.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContainerComponentBuilder {

    @Autowired
    SoftwareSystemRepository softwareSystemRepository;

    @Autowired
    ContainerRepository containerRepository;

    @Autowired
    ComponentRepository componentRepository;

    @Autowired
    EnvironmentRepository environmentRepository;

    @Autowired
    ContainerInstanceRepository containerInstanceRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    public void getComponent(Node node, DiagramParameters diagramParameters) {
        Component component = new Component();
        component.setProperties(new HashMap<>());
        component.setRelationships(new ArrayList<>());
        component.setId(String.valueOf(diagramParameters.getLastObjectId()));
        setComponentProperties(node, component);
        String componentDSLIdentifier = component.getProperties().get("structurizr_dsl_identifier").toString();
        diagramParameters.getObjectMap().put(componentDSLIdentifier, diagramParameters.getLastObjectId());
        diagramParameters.getCreatedComponents().put(componentDSLIdentifier, component);
        diagramParameters.setLastObjectId(diagramParameters.getLastObjectId() + 1);
        addComponentToContainer(component, diagramParameters);
    }

    public void setContainerProperties(Node node, Container container) {
        BeanWrapper wrapper = new BeanWrapperImpl(container);
        for (String key : node.keys()) {
            Object value = node.get(key).asObject();
            if (wrapper.isWritableProperty(key)) {
                wrapper.setPropertyValue(key, value);
            } else {
                container.getProperties().put(key, value);
            }
        }
    }

    public void setComponentProperties(Node node, Component component) {
        for (String key : node.keys()) {
            try {
                Field field = Component.class.getDeclaredField(key);
                field.setAccessible(true);
                field.set(component, node.get(key).asObject());
            } catch (Exception e) {
                component.getProperties().put(key, node.get(key).asObject());
            }
        }
    }

    public void addComponentToContainer(Component component, DiagramParameters diagramParameters) {

        String componentDSLIdentifier = component.getProperties().get("structurizr_dsl_identifier").toString();
        Result result = containerRepository.getParentContainer(componentDSLIdentifier);
        Record record = result.next();
        String containerDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
        if (!diagramParameters.getObjectMap().containsKey(containerDSLIdentifier)) {
            getContainer(record.get("m").asNode(), diagramParameters);
        }
        Container container = diagramParameters.getCreatedContainers().get(containerDSLIdentifier);
        container.getComponents().add(component);
        diagramParameters.getCreatedContainers().put(containerDSLIdentifier, container);
        diagramParameters.getParents().put(componentDSLIdentifier, containerDSLIdentifier);
    }

    public void getDirectComponentRelationships(Component component, SoftwareSystem system,
                                                Container container, String componentDSLIndentifier, Boolean needAddingToSystem,
                                                Boolean needAddingToContainer, DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getDirectComponentRelationships(componentDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            Node destinationNode = record.get("m").asNode();
            Relationship relation = record.get("r").asRelationship();
            String destinationDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            String destinationSystemId = getExternalSystem(destinationNode, destinationDSLIdentifier, diagramParameters).getId();
            RelationshipEntity relationship = addDirectRelationship(component.getId(), system, container, destinationSystemId,
                    destinationDSLIdentifier, relation, needAddingToSystem, needAddingToContainer, diagramParameters);
            if (relationship != null) {
                component.getRelationships().add(relationship);
            }
        }
    }

    public Container getContainer(Node node, DiagramParameters diagramParameters) {
        Long id = diagramParameters.getLastObjectId();
        Container container = new Container();
        container.setProperties(new HashMap<>());
        container.setRelationships(new ArrayList<>());
        container.setComponents(new ArrayList<>());
        container.setId(String.valueOf(id));
        setContainerProperties(node, container);
        String containerDSLIdentifier = container.getProperties().get("structurizr_dsl_identifier").toString();
        diagramParameters.getObjectMap().put(containerDSLIdentifier, diagramParameters.getLastObjectId());
        diagramParameters.getCreatedContainers().put(containerDSLIdentifier, container);
        diagramParameters.setLastObjectId(id + 1);
        addContainerToSystem(container, diagramParameters);
        return container;
    }

    public void getComponents(String containerDSLIdentifier, DiagramParameters diagramParameters) {
        Result result = componentRepository.getComponents(containerDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            diagramParameters.getSystemChilds().add(diagramParameters.getLastObjectId().toString());
            getComponent(record.get("m").asNode(), diagramParameters);
        }
    }

    public void getContainerRelationships(SoftwareSystem system, String systemtDSLIndentifier,
                                          Boolean needAddingToSystem, DiagramParameters diagramParameters) {
        Result result = containerRepository.getContainers(systemtDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String containerDSLIndentifier = record.get("m.structurizr_dsl_identifier").asString();
            Container container = diagramParameters.getCreatedContainers().get(containerDSLIndentifier);
            getDirectContainerRelationships(container, system, containerDSLIndentifier, needAddingToSystem, diagramParameters);
            getReverseContainerRelationships(container, system, containerDSLIndentifier, needAddingToSystem, diagramParameters);
            getComponentRelationships(containerDSLIndentifier, system, container, needAddingToSystem, true,
                    diagramParameters);
            diagramParameters.getCreatedContainers().put(containerDSLIndentifier, container);
        }
    }

    public void createComponentView(String softwareSystemMnemonic, String containerMnemonic,
                                    SoftwareSystem system, DiagramParameters diagramParameters) {
        Result result = containerRepository.getContainer(softwareSystemMnemonic, containerMnemonic);
        getContainer(result.next().get("m").asNode(), diagramParameters);
        getComponents(containerMnemonic, diagramParameters);
        getComponentRelationships(containerMnemonic, system, null, false, false, diagramParameters);
    }

    public void getContainers(String softwareSystemMnemonic, DiagramParameters diagramParameters) {
        Result result = containerRepository.getContainers(softwareSystemMnemonic);
        while (result.hasNext()) {
            Record record = result.next();
            diagramParameters.getSystemChilds().add(diagramParameters.getLastObjectId().toString());
            getContainer(record.get("m").asNode(), diagramParameters);
            String containerDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            getComponents(containerDSLIdentifier, diagramParameters);
        }
    }

    public void getReverseComponentRelationships(Component component, String systemId, String containerId,
                                                 String componentDSLIndentifier, Boolean needAddingToSystem,
                                                 Boolean needAddingToContainer, DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getReverseComponentRelationships(componentDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            Node sourceNode = record.get("m").asNode();
            Relationship relation = record.get("r").asRelationship();
            String sourceDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            if (diagramParameters.getObjectMap().containsKey(sourceDSLIdentifier) && diagramParameters.getSystemChilds()
                    .contains(diagramParameters.getObjectMap().get(sourceDSLIdentifier).toString())) {
                continue;
            }
            SoftwareSystem sourceSystem = getExternalSystem(sourceNode, sourceDSLIdentifier, diagramParameters);
            addReverseRelationship(sourceSystem, component.getId(), systemId, containerId, sourceDSLIdentifier,
                    relation, needAddingToSystem, needAddingToContainer, diagramParameters);
        }
    }

    public void getComponentRelationships(String containerDSLIdentifier, SoftwareSystem system,
                                          Container container, Boolean needAddingToSystem, Boolean needAddingToContainer,
                                          DiagramParameters diagramParameters) {
        Result result = componentRepository.getComponents(containerDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String componentDSLIndentifier = record.get("m.structurizr_dsl_identifier").asString();
            Component component = diagramParameters.getCreatedComponents().get(componentDSLIndentifier);
            getDirectComponentRelationships(component, system, container, componentDSLIndentifier,
                    needAddingToSystem, needAddingToContainer, diagramParameters);
            getReverseComponentRelationships(component, system.getId(), null,
                    componentDSLIndentifier, needAddingToSystem, needAddingToContainer, diagramParameters);
            diagramParameters.getCreatedComponents().put(componentDSLIndentifier, component);
        }
    }

    public void setContainerInstances(DeploymentNode deploymentNode,
                                      String deploymentNodeDSLIdentifier, DiagramParameters diagramParameters) {
        deploymentNode.setContainerInstances(new ArrayList<>());
        Result result = containerInstanceRepository.getContainerInstances(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String containerInstanceDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            ContainerInstance containerInstance = diagramParameters.getCreatedContainerInstances().get(containerInstanceDSLIdentifier);
            getContainerInstanceRelationships(containerInstance, containerInstanceDSLIdentifier, diagramParameters);
            deploymentNode.getContainerInstances().add(containerInstance);
        }
    }

    public void setContainerInstanceProperties(Node node, ContainerInstance containerInstance) {
        for (String key : node.keys()) {
            try {
                Field field = ContainerInstance.class.getDeclaredField(key);
                field.setAccessible(true);
                field.set(containerInstance, node.get(key).asObject());
            } catch (Exception e) {
                containerInstance.getProperties().put(key, node.get(key).asObject());
            }
        }
    }

    public void setContainerInstanceEnvironment(String containerInstanceDSLIdentifier,
                                                ContainerInstance containerInstance) {
        Result result = environmentRepository.getContainerInstanceEnvironment(containerInstanceDSLIdentifier);
        containerInstance.setEnvironment(result.next().get("n.name").asString());
    }

    public void setContainerInstanceContainerId(String containerInstanceDSLIdentifier,
                                                ContainerInstance containerInstance, DiagramParameters diagramParameters) {
        Result result = containerRepository.getContainerInstanceContainerId(containerInstanceDSLIdentifier);
        String containerDSLIdentifier = result.next().get("n.structurizr_dsl_identifier").asString();
        containerInstance.setContainerId(diagramParameters.getObjectMap().get(containerDSLIdentifier).toString());
    }

    public void getContainerInstance(Node node, String environment, DiagramParameters diagramParameters) {
        Long id = diagramParameters.getLastObjectId();
        ContainerInstance containerInstance = new ContainerInstance();
        containerInstance.setProperties(new HashMap<>());
        containerInstance.setRelationships(new ArrayList<>());
        containerInstance.setId(String.valueOf(id));
        setContainerInstanceProperties(node, containerInstance);
        String containerInstanceDSLIdentifier = containerInstance.getProperties().get("structurizr_dsl_identifier").toString();
        setContainerInstanceEnvironment(containerInstanceDSLIdentifier, containerInstance);
        if (!containerInstance.getEnvironment().equals(environment)) {
            return;
        }
        setContainerInstanceContainerId(containerInstanceDSLIdentifier, containerInstance, diagramParameters);
        diagramParameters.getObjectMap().put(containerInstanceDSLIdentifier, id);
        diagramParameters.setLastObjectId(id + 1);
        diagramParameters.getCreatedContainerInstances().put(containerInstanceDSLIdentifier, containerInstance);
    }

    public void getContainerInstances(String deploymentNodeDSLIdentifier, String environment,
                                      DiagramParameters diagramParameters) {
        Result result = containerInstanceRepository.getContainerInstances(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            getContainerInstance(record.get("m").asNode(), environment, diagramParameters);
        }
    }

    public SoftwareSystem getSystem(String systemDSLIdentifier, DiagramParameters diagramParameters) {
        Result result = softwareSystemRepository.getSystem(systemDSLIdentifier);
        Node node = result.next().get("n").asNode();
        SoftwareSystem softwareSystem = new SoftwareSystem();
        softwareSystem.setProperties(new HashMap<>());
        softwareSystem.setRelationships(new ArrayList<>());
        softwareSystem.setContainers(new ArrayList<>());
        softwareSystem.setId(String.valueOf(diagramParameters.getLastObjectId()));
        setSystemProperties(node, softwareSystem);
        diagramParameters.getObjectMap().put(systemDSLIdentifier, diagramParameters.getLastObjectId());
        diagramParameters.getCreatedSystems().put(systemDSLIdentifier, softwareSystem);
        diagramParameters.setLastObjectId(diagramParameters.getLastObjectId() + 1);
        return softwareSystem;
    }

    public void setSystemProperties(Node node, SoftwareSystem softwareSystem) {
        for (String key : node.keys()) {
            try {
                Field field = SoftwareSystem.class.getDeclaredField(key);
                field.setAccessible(true);
                field.set(softwareSystem, node.get(key).asObject());
            } catch (Exception e) {
                softwareSystem.getProperties().put(key, node.get(key).asObject());
            }
        }
    }

    public void addContainerToSystem(Container container, DiagramParameters diagramParameters) {
        String containerDSLIdentifier = container.getProperties().get("structurizr_dsl_identifier").toString();
        Result result = softwareSystemRepository.getParentSystem(containerDSLIdentifier);
        String systemDSLIdentifier = result.next().get("m.structurizr_dsl_identifier").asString();
        if (!diagramParameters.getObjectMap().containsKey(systemDSLIdentifier)) {
            getSystem(systemDSLIdentifier, diagramParameters);
        }
        SoftwareSystem system = diagramParameters.getCreatedSystems().get(systemDSLIdentifier);
        system.getContainers().add(container);
        diagramParameters.getCreatedSystems().put(systemDSLIdentifier, system);
        diagramParameters.getParents().put(containerDSLIdentifier, systemDSLIdentifier);
    }

    public SoftwareSystem getExternalSystem(Node node, String nodeDSLIdentifier, DiagramParameters diagramParameters) {
        String label = node.labels().toString();
        label = label.substring(1, label.length() - 1);
        switch (label) {
            case "SoftwareSystem":
                if (!diagramParameters.getObjectMap().containsKey(nodeDSLIdentifier)) {
                    getSystem(nodeDSLIdentifier, diagramParameters);
                }
                return diagramParameters.getCreatedSystems().get(nodeDSLIdentifier);
            case "Container":
                if (!diagramParameters.getObjectMap().containsKey(nodeDSLIdentifier)) {
                    getContainer(node, diagramParameters);
                }
                return diagramParameters.getCreatedSystems().get(diagramParameters.getParents().get(nodeDSLIdentifier));
            case "Component":
                if (!diagramParameters.getObjectMap().containsKey(nodeDSLIdentifier)) {
                    getComponent(node, diagramParameters);
                }
                return diagramParameters.getCreatedSystems()
                        .get(diagramParameters.getParents().get(diagramParameters.getParents().get(nodeDSLIdentifier)));
            default:
                return null;
        }
    }

    public void getDirectSystemRelationships(SoftwareSystem system, String systemtDSLIndentifier,
                                             DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getDirectSystemRelationships(systemtDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            Node destinationNode = record.get("m").asNode();
            Relationship relation = record.get("r").asRelationship();
            String destinationDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            String destinationSystemId = getExternalSystem(destinationNode, destinationDSLIdentifier, diagramParameters).getId();
            RelationshipEntity relationship = getRelationship(relation, system.getId(), destinationSystemId, diagramParameters);
            if (relationship != null) {
                system.getRelationships().add(relationship);
            }
        }
    }

    public void getReverseSystemRelationships(SoftwareSystem system, String systemtDSLIndentifier,
                                              DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getReverseComponentRelationships(systemtDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            Node sourceNode = record.get("m").asNode();
            Relationship relation = record.get("r").asRelationship();
            String sourceDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            if (diagramParameters.getObjectMap().containsKey(sourceDSLIdentifier) && diagramParameters.getSystemChilds()
                    .contains(diagramParameters.getObjectMap().get(sourceDSLIdentifier).toString())) {
                continue;
            }
            SoftwareSystem sourceSystem = getExternalSystem(sourceNode, sourceDSLIdentifier, diagramParameters);
            addReverseRelationship(sourceSystem, system.getId(), system.getId(), null, sourceDSLIdentifier, relation,
                    false, false, diagramParameters);
        }
    }

    public void getSystemRelationships(SoftwareSystem system, String systemtDSLIndentifier, DiagramParameters diagramParameters) {
        getDirectSystemRelationships(system, systemtDSLIndentifier, diagramParameters);
        getReverseSystemRelationships(system, systemtDSLIndentifier, diagramParameters);
    }

    public void getReverseContainerRelationships(Container container, SoftwareSystem system, String containerDSLIndentifier,
                                                 Boolean needAddingToSystem, DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getReverseContainerRelationships(containerDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            Node sourceNode = record.get("m").asNode();
            Relationship relation = record.get("r").asRelationship();
            String sourceDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            if (diagramParameters.getObjectMap().containsKey(sourceDSLIdentifier) && diagramParameters.getSystemChilds()
                    .contains(diagramParameters.getObjectMap().get(sourceDSLIdentifier).toString())) {
                continue;
            }
            SoftwareSystem sourceSystem = getExternalSystem(sourceNode, sourceDSLIdentifier, diagramParameters);
            addReverseRelationship(sourceSystem, container.getId(), system.getId(), null, sourceDSLIdentifier, relation,
                    needAddingToSystem, false, diagramParameters);
        }
    }


    public void getDirectContainerRelationships(Container container, SoftwareSystem system, String containerDSLIndentifier,
                                                Boolean needAddingToSystem, DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getDirectContainerRelationships(containerDSLIndentifier);
        while (result.hasNext()) {
            Record record = result.next();
            Node destinationNode = record.get("m").asNode();
            Relationship relation = record.get("r").asRelationship();
            String destinationDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            String destinationSystemId = getExternalSystem(destinationNode, destinationDSLIdentifier,
                    diagramParameters).getId();
            RelationshipEntity relationship = addDirectRelationship(container.getId(), system, null, destinationSystemId,
                    destinationDSLIdentifier, relation, needAddingToSystem, false, diagramParameters);
            if (relationship != null) {
                container.getRelationships().add(relationship);
            }
        }
    }

    public void addReverseRelationship(SoftwareSystem sourceSystem, String destinationtId, String systemId,
                                       String containerId, String sourceDSLIdentifier, Relationship relation,
                                       Boolean needAddingToSystem, Boolean needAddingToContainer, DiagramParameters diagramParameters) {
        RelationshipEntity relationship = getRelationship(relation, sourceSystem.getId(), destinationtId, diagramParameters);
        if (relationship != null) {
            sourceSystem.getRelationships().add(relationship);
        }
        if (needAddingToSystem && !sourceSystem.getId().equals(systemId)) {
            relationship = getRelationship(relation, sourceSystem.getId(), systemId, diagramParameters);
            if (relationship != null) {
                sourceSystem.getRelationships().add(relationship);
            }
        }
        if (needAddingToContainer && !sourceSystem.getId().equals(systemId)) {
            relationship = getRelationship(relation, sourceSystem.getId(), containerId, diagramParameters);
            if (relationship != null) {
                sourceSystem.getRelationships().add(relationship);
            }
        }
        diagramParameters.getCreatedSystems().put(sourceDSLIdentifier, sourceSystem);
    }

    public RelationshipEntity getRelationship(Relationship relation, String source, String destination, DiagramParameters diagramParameters) {
        RelationshipEntity relationship = new RelationshipEntity();
        relationship.setProperties(new HashMap<>());
        relationship.setSourceId(source);
        relationship.setDestinationId(destination);
        setRelationshipProperties(relation, relationship);
        if (checkIfRelationshipExists(relationship, source, destination, diagramParameters)) {
            return null;
        }
        relationship.setId(String.valueOf(diagramParameters.getLastObjectId()));
        diagramParameters.setLastObjectId(diagramParameters.getLastObjectId() + 1);
        return relationship;
    }

    public void setRelationshipProperties(Relationship relation, RelationshipEntity relationship) {
        for (Map.Entry<String, Object> entry : relation.asMap().entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            try {
                Field field = RelationshipEntity.class.getDeclaredField(key);
                field.setAccessible(true);
                field.set(relationship, val);
            } catch (Exception e) {
                relationship.getProperties().put(key, val);
            }
        }
    }

    public Boolean checkIfRelationshipExists(RelationshipEntity relationship, String source, String destination,
                                             DiagramParameters diagramParameters) {
        Edge edge = new Edge(source, destination, relationship.getDescription());
        if (diagramParameters.getCreatedRelationships().contains(edge)) {
            return true;
        }
        diagramParameters.getCreatedRelationships().add(edge);
        return false;
    }

    public RelationshipEntity addDirectRelationship(String sourceId, SoftwareSystem system, Container container,
                                                    String destinationSystemId, String destinationDSLIdentifier, Relationship relation,
                                                    Boolean needAddingToSystem, Boolean needAddingToContainer, DiagramParameters diagramParameters) {
        if (!destinationSystemId.equals(system.getId())) {
            RelationshipEntity relationship = getRelationship(relation, sourceId, destinationSystemId, diagramParameters);
            if (needAddingToSystem) {
                RelationshipEntity systemRelationship = getRelationship(relation, system.getId(), destinationSystemId, diagramParameters);
                if (systemRelationship != null) {
                    system.getRelationships().add(systemRelationship);
                }
            }
            if (needAddingToContainer) {
                RelationshipEntity containerRelationship = getRelationship(relation, container.getId(), destinationSystemId,
                        diagramParameters);
                if (containerRelationship != null) {
                    container.getRelationships().add(containerRelationship);
                }
            }
            return relationship;
        } else {
            String destinationId = diagramParameters.getObjectMap().get(destinationDSLIdentifier).toString();
            return getRelationship(relation, sourceId, destinationId, diagramParameters);
        }
    }

    public void getContainerInstanceRelationships(ContainerInstance containerInstance,
                                                  String containerInstanceDSLIdentifier, DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getContainerInstanceRelationships(containerInstanceDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String destinationDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            String destinationId = diagramParameters.getObjectMap().get(destinationDSLIdentifier).toString();
            RelationshipEntity relationship = getRelationship(record.get("r").asRelationship(), containerInstance.getId(),
                    destinationId, diagramParameters);
            if (relationship != null) {
                containerInstance.getRelationships().add(relationship);
            }
        }
    }

    public List<ContainerNodeDTO> findContainersWithParentCmdb(String search) {
        if (search == null || search.isEmpty()) {
            throw new ValidationException("Отсутствует обязательный параметр search");
        }
        Result result = containerRepository.findContainersWithParentCmdb(search);
        List<ContainerNodeDTO> response = new ArrayList<>();
        while (result.hasNext()) {
            Record record = result.next();
            response.add(ContainerNodeDTO.builder()
                    .containerName(record.get("containerName").asString())
                    .cmdb(record.get("cmdb").asString())
                    .build());
        }
        return response;
    }
}
