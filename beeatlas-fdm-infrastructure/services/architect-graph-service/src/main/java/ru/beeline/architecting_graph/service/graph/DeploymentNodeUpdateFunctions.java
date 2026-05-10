/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.repository.neo4j.GenericRepository;
import ru.beeline.architecting_graph.repository.neo4j.DeploymentNodesRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class DeploymentNodeUpdateFunctions {

    @Autowired
    GenericRepository genericRepository;

    @Autowired
    ContainerInstanceService containerInstanceService;

    @Autowired
    InfrastructureNodeUpdateFunctions infrastructureNodeUpdateFunctions;

    @Autowired
    CreateExternalObjects createExternalObjects;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    public void setDeploymentNodeProperties(String graphTag, DeploymentNode deploymentNode) {
        if (deploymentNode.getProperties() != null) {
            for (Map.Entry<String, Object> entry : deploymentNode.getProperties().entrySet()) {
                String key = entry.getKey().replaceAll("[^a-zA-Z0-9]", "_");
                deploymentNodesRepository.setDeploymentNodeProperty(graphTag, deploymentNode.getName(), key, entry.getValue());
            }
        }
    }

    public void setParametersForDeploymentNode(String graphTag, DeploymentNode deploymentNode,
                                               GraphObject deploymentNodeGraphObject, String curVersion) {
        if (graphTag.equals("Global") && genericRepository.getObjectParameter(graphTag, deploymentNodeGraphObject,
                                                                              "startVersion").toString().equals("NULL")) {
            genericRepository.setObjectParameter(graphTag, deploymentNodeGraphObject, "startVersion", curVersion);
        }
        deploymentNodesRepository.updateDeploymentNode(graphTag, deploymentNode);
        setDeploymentNodeProperties(graphTag, deploymentNode);
    }

    public void createEnvironmentRelation(String graphTag, String environment, String NodeId, String curVersion,
                                          String cmdb, Model model, HashMap<String, GraphObject> objects) {
        updateEnvironment(graphTag, environment, objects);
        createExternalObjects.updateChildRelationship(graphTag, model, curVersion, environment,
                NodeId,
                cmdb, objects);
    }

    public void updateEnvironment(String graphTag, String environment,
                                  HashMap<String, GraphObject> objects) {
        GraphObject environmentGraphObject = new GraphObject("Environment", "name", environment);
        if (!genericRepository.checkIfObjectExists(graphTag, environmentGraphObject)) {
            genericRepository.createObject(graphTag, environmentGraphObject);
        }
        if (!objects.containsKey(environment)) {
            objects.put(environment, environmentGraphObject);
        }
    }

    public void updateChildInfrastructureNodes(String graphTag, DeploymentNode deploymentNode,
                                               String curVersion, String cmdb, Model model, HashMap<String, GraphObject> objects) {
        if (deploymentNode.getInfrastructureNodes() != null) {
            for (InfrastructureNode infrastructureNode : deploymentNode.getInfrastructureNodes()) {
                infrastructureNode.setOriginalName(new String(infrastructureNode.getName()));
                infrastructureNode.setName(infrastructureNode.getName() + "~"
                        + deploymentNode.getName().toString());
                infrastructureNodeUpdateFunctions.updateInfrastructureNode(graphTag, infrastructureNode, curVersion,
                        objects);
                createExternalObjects.updateChildRelationship(graphTag, model, curVersion,
                        deploymentNode.getId(), infrastructureNode.getId(), cmdb, objects);
                createEnvironmentRelation(graphTag, infrastructureNode.getEnvironment(), infrastructureNode.getId(),
                        curVersion, cmdb, model, objects);
            }
        }
    }

    public void updateChildContainerInstances(String graphTag, DeploymentNode deploymentNode, String curVersion,
                                              String cmdb, Model model, HashMap<String, GraphObject> objects) {
        if (deploymentNode.getContainerInstances() != null) {
            for (ContainerInstance containerInstance : deploymentNode.getContainerInstances()) {
                containerInstanceService.updateContainerInstance(graphTag, model, deploymentNode,
                                                                 containerInstance, curVersion, objects);
                createExternalObjects.updateDeployRelationship(graphTag, model, curVersion,
                        containerInstance.getContainerId(), containerInstance.getId(), cmdb, objects);
                createExternalObjects.updateChildRelationship(graphTag, model, curVersion, deploymentNode.getId(),
                        containerInstance.getId(), cmdb, objects);
                createEnvironmentRelation(graphTag, containerInstance.getEnvironment(), containerInstance.getId(),
                        curVersion, cmdb, model, objects);
            }
        }
    }

    public void updateChildDeploymentNodes(String graphTag, DeploymentNode deploymentNode,
                                           String curVersion, String cmdb, Model model, HashMap<String, GraphObject> objects) {
        if (deploymentNode.getChildren() != null) {
            for (DeploymentNode childDeploymentNode : deploymentNode.getChildren()) {
                childDeploymentNode.setOriginalName(new String(childDeploymentNode.getName()));
                childDeploymentNode.setName(childDeploymentNode.getName() + "~" + deploymentNode.getName().toString());
                updateDeploymentNode(graphTag, childDeploymentNode, curVersion, cmdb, model, objects);
                createExternalObjects.updateChildRelationship(graphTag, model, curVersion, deploymentNode.getId(),
                        childDeploymentNode.getId(), cmdb, objects);
            }
        }
    }

    public void updateDeploymentNode(String graphTag, DeploymentNode deploymentNode,
                                     String curVersion, String cmdb, Model model, HashMap<String, GraphObject> objects) {
        GraphObject deploymentNodeGraphObject = createDeploymentNodeGraphObject(graphTag, deploymentNode, objects);
        setParametersForDeploymentNode(graphTag, deploymentNode, deploymentNodeGraphObject, curVersion);
        createEnvironmentRelation(graphTag, deploymentNode.getEnvironment(), deploymentNode.getId(),
                curVersion, cmdb, model, objects);
        updateChildInfrastructureNodes(graphTag, deploymentNode, curVersion, cmdb, model, objects);
        updateChildContainerInstances(graphTag, deploymentNode, curVersion, cmdb, model, objects);
        updateChildDeploymentNodes(graphTag, deploymentNode, curVersion, cmdb, model, objects);
    }

    private GraphObject createDeploymentNodeGraphObject(String graphTag,
                                       DeploymentNode deploymentNode,
                                       HashMap<String, GraphObject> objects) {
        GraphObject deploymentNodeGraphObject = new GraphObject("DeploymentNode", "name", deploymentNode.getName());
        boolean exists = genericRepository.checkIfObjectExists(graphTag, deploymentNodeGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, deploymentNodeGraphObject);
        }
        if (deploymentNode.getOriginalName() != null) {
            genericRepository.setObjectParameter(
                    graphTag,
                    deploymentNodeGraphObject,
                    "originalName",
                    deploymentNode.getOriginalName());
        }
        objects.put(deploymentNode.getId(), deploymentNodeGraphObject);
        return deploymentNodeGraphObject;
    }

    public void setChildDeploymentNodeEndVersion(String graphTag, String deploymentNodeName,
                                                 String curVersion, String cmdb) {
        Result result = deploymentNodesRepository.findChildDeploymentNodesWithNullEndVersion(graphTag, deploymentNodeName);
        while (result.hasNext()) {
            String childDeploymentNodeName = result.next().get("childDeploymentNodeName").toString();
            childDeploymentNodeName = childDeploymentNodeName.substring(1, childDeploymentNodeName.length() - 1);
            setDeploymentNodeEndVersion(graphTag, childDeploymentNodeName, curVersion, cmdb);
        }
    }

    public void setDeploymentNodeEndVersion(String graphTag, String deploymentNodeName,
                                            String curVersion, String cmdb) {
        GraphObject deploymentNodeGraphObject = new GraphObject("DeploymentNode", "name", deploymentNodeName);
        genericRepository.setObjectParameter(graphTag, deploymentNodeGraphObject, "endVersion", curVersion);
        infrastructureNodeUpdateFunctions.setInfrastructureNodeEndVersion(graphTag, deploymentNodeName, curVersion);
        containerInstanceService.setContainerInstanceEndVersion(graphTag, deploymentNodeName, curVersion);
        setChildDeploymentNodeEndVersion(graphTag, deploymentNodeName, curVersion, cmdb);
    }

    public void updateChildDeploymentNodeRelationships(String graphTag, DeploymentNode deploymentNode,
                                                       String curVersion, String cmdb, Model model, HashMap<String, GraphObject> objects) {
        if (deploymentNode.getChildren() != null) {
            for (DeploymentNode childDeploymentNode : deploymentNode.getChildren()) {
                updateDeploymentNodeRelationships(graphTag, childDeploymentNode, curVersion, cmdb, model, objects);
            }
        }
    }

    public void updateDeploymentNodeRelationships(String graphTag, DeploymentNode deploymentNode, String curVersion,
                                                  String cmdb, Model model, HashMap<String, GraphObject> objects) {
        updateDefaultRelationship(graphTag, deploymentNode, curVersion, cmdb, model, objects);
        updateInfrastructureNodeRelationships(graphTag, deploymentNode, curVersion, cmdb, model, objects);
        containerInstanceService.updateContainerInstanceRelationships(graphTag, deploymentNode,
                                                                      curVersion, cmdb, model, objects);
        updateChildDeploymentNodeRelationships(graphTag, deploymentNode, curVersion, cmdb, model, objects);
    }

    private void updateDefaultRelationship(String graphTag,
                           DeploymentNode deploymentNode,
                           String curVersion,
                           String cmdb,
                           Model model,
                           HashMap<String, GraphObject> objects) {
        if (deploymentNode.getRelationships() != null) {
            for (RelationshipEntity relationship : deploymentNode.getRelationships()) {
                createExternalObjects.updateDefaultRelationship(graphTag, relationship,
                                                                model,
                                                                curVersion,
                                                                cmdb, "",
                                                                objects);
            }
        }
    }

    public void updateInfrastructureNodeRelationships(String graphTag, DeploymentNode deploymentNode,
                                                      String curVersion, String cmdb, Model model, HashMap<String, GraphObject> objects) {
        if (deploymentNode.getInfrastructureNodes() != null) {
            for (InfrastructureNode infrastructureNode : deploymentNode.getInfrastructureNodes()) {
                if (infrastructureNode.getRelationships() != null) {
                    for (RelationshipEntity relationship : infrastructureNode.getRelationships()) {
                        createExternalObjects.updateDefaultRelationship(graphTag, relationship, model, curVersion,
                                cmdb, "", objects);
                    }
                }
            }
        }
    }
}
