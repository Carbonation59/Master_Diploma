/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.createDiagrams;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.types.Node;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.DeploymentNode;
import ru.beeline.architecting_graph.model.DiagramParameters;
import ru.beeline.architecting_graph.model.InfrastructureNode;
import ru.beeline.architecting_graph.model.RelationshipEntity;
import ru.beeline.architecting_graph.repository.neo4j.DeploymentNodesRepository;
import ru.beeline.architecting_graph.repository.neo4j.EnvironmentRepository;
import ru.beeline.architecting_graph.repository.neo4j.InfrastructureNodesRepository;
import ru.beeline.architecting_graph.repository.neo4j.RelationshipRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

@Service
public class NodesBuilder {

    @Autowired
    ContainerComponentBuilder containerComponentBuilder;

    @Autowired
    EnvironmentRepository environmentRepository;

    @Autowired
    InfrastructureNodesRepository infrastructureNodesRepository;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    public void setInfrastructureNodeProperties(Node node, InfrastructureNode infrastructureNode) {
        for (String key : node.keys()) {
            try {
                Field field = InfrastructureNode.class.getDeclaredField(key);
                field.setAccessible(true);
                field.set(infrastructureNode, node.get(key).asObject());
            } catch (Exception e) {
                infrastructureNode.getProperties().put(key, node.get(key).asObject());
            }
        }
    }

    public void setInfrastructureNodeEnvironment(String infrastructureNodeDSLIdentifier,
                                                 InfrastructureNode infrastructureNode) {
        Result result = environmentRepository.getInfrastructureNodeEnvironment(infrastructureNodeDSLIdentifier);
        infrastructureNode.setEnvironment(result.next().get("n.name").asString());
    }

    public void getInfrastructureNode(Node node, String environment, DiagramParameters diagramParameters) {
        InfrastructureNode infrastructureNode = new InfrastructureNode();
        infrastructureNode.setProperties(new HashMap<>());
        infrastructureNode.setRelationships(new ArrayList<>());
        infrastructureNode.setId(String.valueOf(diagramParameters.getLastObjectId()));
        setInfrastructureNodeProperties(node, infrastructureNode);
        String infrastructureNodeDSLIdentifier = infrastructureNode.getProperties()
                .get("structurizr_dsl_identifier")
                .toString();
        setInfrastructureNodeEnvironment(infrastructureNodeDSLIdentifier, infrastructureNode);
        if (!infrastructureNode.getEnvironment().equals(environment)) {
            return;
        }
        diagramParameters.getObjectMap().put(infrastructureNodeDSLIdentifier, diagramParameters.getLastObjectId());
        diagramParameters.setLastObjectId(diagramParameters.getLastObjectId() + 1);
        diagramParameters.getCreatedInfrastructureNodes().put(infrastructureNodeDSLIdentifier, infrastructureNode);
    }

    public void getInfrastructureNodeRelationships(InfrastructureNode infrastructureNode,
                                                   String infrastructureNodeDSLIdentifier,
                                                   DiagramParameters diagramParameters) {
        Result result = relationshipRepository.getInfrastructureNodeRelationships(infrastructureNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String destinationDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            String destinationId = diagramParameters.getObjectMap().get(destinationDSLIdentifier).toString();
            RelationshipEntity relationship = containerComponentBuilder.getRelationship(record.get("r")
                                                                                                .asRelationship(),
                                                                                        infrastructureNode.getId(),
                                                                                        destinationId,
                                                                                        diagramParameters);
            if (relationship != null) {
                infrastructureNode.getRelationships().add(relationship);
            }
        }
    }

    public void setInfrastructureNodes(DeploymentNode deploymentNode,
                                       String deploymentNodeDSLIdentifier,
                                       DiagramParameters diagramParameters) {
        deploymentNode.setInfrastructureNodes(new ArrayList<>());
        Result result = infrastructureNodesRepository.getInfrastructureNodes(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String infrastructureNodeDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            InfrastructureNode infrastructureNode = diagramParameters.getCreatedInfrastructureNodes()
                    .get(infrastructureNodeDSLIdentifier);
            getInfrastructureNodeRelationships(infrastructureNode, infrastructureNodeDSLIdentifier, diagramParameters);
            deploymentNode.getInfrastructureNodes().add(infrastructureNode);
        }
    }

    public void getDeploymentNode(Node node, String environment, DiagramParameters diagramParameters) {
        Long id = diagramParameters.getLastObjectId();
        DeploymentNode deploymentNode = new DeploymentNode();
        deploymentNode.setId(String.valueOf(id));
        deploymentNode.setProperties(new HashMap<>());
        setDeploymentNodeProperties(node, deploymentNode);
        String deploymentNodeDSLIdentifier = deploymentNode.getProperties()
                .get("structurizr_dsl_identifier")
                .toString();
        setDeploymentNodeEnvironment(deploymentNodeDSLIdentifier, deploymentNode);
        if (!deploymentNode.getEnvironment().equals(environment)) {
            return;
        }
        diagramParameters.getObjectMap().put(deploymentNodeDSLIdentifier, id);
        diagramParameters.setLastObjectId(id + 1);
        getInfrastructureNodes(deploymentNodeDSLIdentifier, environment, diagramParameters);
        containerComponentBuilder.getContainerInstances(deploymentNodeDSLIdentifier, environment, diagramParameters);
        getChildDeploymentNodes(deploymentNodeDSLIdentifier, environment, diagramParameters);
        diagramParameters.getCreatedDeploymentNodes().put(deploymentNodeDSLIdentifier, deploymentNode);
    }

    public void setDeploymentNodeRelationships(DeploymentNode deploymentNode,
                                               String deploymentNodeDSLIdentifier,
                                               DiagramParameters diagramParameters) {
        deploymentNode.setRelationships(new ArrayList<>());
        Result result = relationshipRepository.getDeploymentNodeRelationships(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String destinationDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            String destinationId = diagramParameters.getObjectMap().get(destinationDSLIdentifier).toString();
            RelationshipEntity relationship = containerComponentBuilder.getRelationship(record.get("r")
                                                                                                .asRelationship(),
                                                                                        deploymentNode.getId(),
                                                                                        destinationId,
                                                                                        diagramParameters);
            if (relationship != null) {
                deploymentNode.getRelationships().add(relationship);
            }
        }
    }

    public void getChildDeploymentNodes(String deploymentNodeDSLIdentifier,
                                        String environment,
                                        DiagramParameters diagramParameters) {
        Result result = deploymentNodesRepository.getChildDeploymentNodes(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            getDeploymentNode(record.get("m").asNode(), environment, diagramParameters);
        }
    }

    public void setDeploymentNodeProperties(Node node, DeploymentNode deploymentNode) {
        BeanWrapper wrapper = new BeanWrapperImpl(deploymentNode);
        for (String key : node.keys()) {
            Object value = node.get(key).asObject();
            if (wrapper.isWritableProperty(key)) {
                wrapper.setPropertyValue(key, value);
            } else {
                deploymentNode.getProperties().put(key, value);
            }
        }
    }

    public void setDeploymentNodeEnvironment(String deploymentNodeDSLIdentifier, DeploymentNode deploymentNode) {
        Result result = environmentRepository.getDeploymentNodeEnvironment(deploymentNodeDSLIdentifier);
        deploymentNode.setEnvironment(result.next().get("n.name").asString());
    }

    public void getInfrastructureNodes(String deploymentNodeDSLIdentifier,
                                       String environment,
                                       DiagramParameters diagramParameters) {
        Result result = infrastructureNodesRepository.getInfrastructureNodes(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            getInfrastructureNode(record.get("m").asNode(), environment, diagramParameters);
        }
    }

    public void setChildDeploymentNodes(DeploymentNode deploymentNode,
                                        String deploymentNodeDSLIdentifier,
                                        DiagramParameters diagramParameters) {
        deploymentNode.setChildren(new ArrayList<>());
        Result result = deploymentNodesRepository.getChildDeploymentNodes(deploymentNodeDSLIdentifier);
        while (result.hasNext()) {
            Record record = result.next();
            String childDeploymentNodeDSLIdentifier = record.get("m.structurizr_dsl_identifier").asString();
            deploymentNode.getChildren()
                    .add(getDeploymentNodeRelationships(diagramParameters.getCreatedDeploymentNodes()
                                                                .get(childDeploymentNodeDSLIdentifier),
                                                        diagramParameters));
        }
    }

    public DeploymentNode getDeploymentNodeRelationships(DeploymentNode deploymentNode,
                                                         DiagramParameters diagramParameters) {
        String deploymentNodeDSLIdentifier = deploymentNode.getProperties()
                .get("structurizr_dsl_identifier")
                .toString();
        setDeploymentNodeRelationships(deploymentNode, deploymentNodeDSLIdentifier, diagramParameters);
        setInfrastructureNodes(deploymentNode, deploymentNodeDSLIdentifier, diagramParameters);
        containerComponentBuilder.setContainerInstances(deploymentNode, deploymentNodeDSLIdentifier, diagramParameters);
        setChildDeploymentNodes(deploymentNode, deploymentNodeDSLIdentifier, diagramParameters);
        return deploymentNode;
    }
}
