/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.repository.neo4j.GenericRepository;
import ru.beeline.architecting_graph.repository.neo4j.ContainerInstanceRepository;
import ru.beeline.architecting_graph.repository.neo4j.ContainerRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class ContainerInstanceUpdateFunctions {

    @Autowired
    GenericRepository genericRepository;

    @Autowired
    CreateExternalObjects createExternalObjects;
    @Autowired
    private ContainerInstanceRepository containerInstanceRepository;
    @Autowired
    private ContainerRepository containerRepository;

    public void setContainerInstanceProperties(String graphTag,
                                               ContainerInstance containerInstance, String containerInstanceName) {
        if (containerInstance.getProperties() != null) {
            for (Map.Entry<String, Object> entry : containerInstance.getProperties().entrySet()) {
                String key = entry.getKey().replaceAll("[^a-zA-Z0-9]", "_"); // 👉 оставить тут как бизнес-логику
                containerInstanceRepository.setProperty(graphTag, containerInstanceName, key, entry.getValue());
            }
        }
    }

    public void setParametersForContainerInstance(String graphTag, ContainerInstance containerInstance,
                                                  GraphObject containerInstanceGraphObject, String curVersion) {
        if (graphTag.equals("Global")
                && genericRepository.getObjectParameter(graphTag, containerInstanceGraphObject, "startVersion")
                .toString().equals("NULL")) {
            genericRepository.setObjectParameter(graphTag, containerInstanceGraphObject, "startVersion", curVersion);
        }
        containerInstanceRepository.updateContainerInstance(graphTag, containerInstanceGraphObject.getValue(),
                                                            containerInstance.getInstanceId(), containerInstance.getTags());
        setContainerInstanceProperties(graphTag, containerInstance, containerInstanceGraphObject.getValue());
    }

    public static String getContainerforContainerInstance(Model model, String containerInstanceContainerId) {
        for (SoftwareSystem softwareSystem : model.getSoftwareSystems()) {
            if (softwareSystem.getContainers() != null) {
                for (Container container : softwareSystem.getContainers()) {
                    if (container.getId().equals(containerInstanceContainerId)) {
                        return container.getName().toString();
                    }
                }
            }
        }
        return null;
    }

    public void updateContainerInstance(String graphTag, Model model, DeploymentNode deploymentNode,
                                        ContainerInstance containerInstance, String curVersion, HashMap<String, GraphObject> objects) {
        String containerInstanceName = getContainerforContainerInstance(model, containerInstance.getContainerId());
        if (containerInstanceName == null) {
            return;
        }
        containerInstanceName = containerInstanceName + ".ContainerInstance." + deploymentNode.getName().toString();
        GraphObject containerInstanceGraphObject = new GraphObject("ContainerInstance", "name",
                containerInstanceName);
        if (!genericRepository.checkIfObjectExists(graphTag, containerInstanceGraphObject)) {
            genericRepository.createObject(graphTag, containerInstanceGraphObject);
        }
        objects.put(containerInstance.getId(), containerInstanceGraphObject);
        setParametersForContainerInstance(graphTag, containerInstance, containerInstanceGraphObject, curVersion);
    }

    public void setContainerInstanceEndVersion(String graphTag, String deploymentNodeName, String curVersion) {
        Result result = containerInstanceRepository.findContainerInstanceNamesWithNullEndVersion(graphTag, deploymentNodeName);
        while (result.hasNext()) {
            String rawName = result.next().get("containerInstanceName").toString();
            String cleanedName = rawName.substring(1, rawName.length() - 1);
            GraphObject containerInstanceGraphObject = new GraphObject("ContainerInstance", "name", cleanedName);
            genericRepository.setObjectParameter(graphTag, containerInstanceGraphObject, "endVersion", curVersion);
        }
    }

    public void updateContainerInstanceRelationships(String graphTag, DeploymentNode deploymentNode, String curVersion,
                                                     String cmdb, Model model, HashMap<String, GraphObject> objects) {
        if (deploymentNode.getContainerInstances() != null) {
            for (ContainerInstance containerInstance : deploymentNode.getContainerInstances()) {
                if (containerInstance.getRelationships() != null) {
                    for (RelationshipEntity relationship : containerInstance.getRelationships()) {
                        createExternalObjects.updateDefaultRelationship(graphTag, relationship, model,
                                curVersion, cmdb, "", objects);
                    }
                }
            }
        }
    }
}
