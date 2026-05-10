/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.beeline.architecting_graph.model.GraphObject;
import ru.beeline.architecting_graph.model.InfrastructureNode;
import ru.beeline.architecting_graph.repository.neo4j.GenericRepository;
import ru.beeline.architecting_graph.repository.neo4j.InfrastructureNodesRepository;

import java.util.HashMap;
import java.util.Map;

@Component
public class InfrastructureNodeUpdateFunctions {

    @Autowired
    GenericRepository genericRepository;

    @Autowired
    InfrastructureNodesRepository infrastructureNodesRepository;

    public void setInfrastructureNodeProperties(String graphTag, InfrastructureNode infrastructureNode) {
        if (infrastructureNode.getProperties() != null) {
            for (Map.Entry<String, Object> entry : infrastructureNode.getProperties().entrySet()) {
                String rawKey = entry.getKey();
                String cleanedKey = rawKey.replaceAll("[^a-zA-Z0-9]", "_");
                infrastructureNodesRepository.setInfrastructureNodeProperty(graphTag, infrastructureNode.getName(),
                                                                            cleanedKey, entry.getValue());
            }
        }
    }

    public void setParametersForInfrastructureNode(String graphTag, InfrastructureNode infrastructureNode,
                                                   GraphObject infrastructureNodeGraphObject, String curVersion) {
        if ("Global".equals(graphTag)
                && genericRepository.getObjectParameter(graphTag, infrastructureNodeGraphObject, "startVersion")
                .toString().equals("NULL")) {
            genericRepository.setObjectParameter(graphTag, infrastructureNodeGraphObject, "startVersion", curVersion);
        }
        infrastructureNodesRepository.updateInfrastructureNode(graphTag, infrastructureNode.getName(), infrastructureNode.getDescription(),
                                                               infrastructureNode.getTechnology(), infrastructureNode.getTags(), infrastructureNode.getUrl(), null);
        setInfrastructureNodeProperties(graphTag, infrastructureNode);
    }


    public void updateInfrastructureNode(String graphTag, InfrastructureNode infrastructureNode, String curVersion,
                                         HashMap<String, GraphObject> objects) {
        GraphObject infrastructureNodeGraphObject = new GraphObject("InfrastructureNode", "name",
                infrastructureNode.getName());
        boolean exists = genericRepository.checkIfObjectExists(graphTag, infrastructureNodeGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, infrastructureNodeGraphObject);
        }
        if (infrastructureNode.getOriginalName() != null) {
            genericRepository.setObjectParameter(
                    graphTag,
                    infrastructureNodeGraphObject,
                    "originalName",
                    infrastructureNode.getOriginalName());
        }
        objects.put(infrastructureNode.getId(), infrastructureNodeGraphObject);
        setParametersForInfrastructureNode(graphTag, infrastructureNode, infrastructureNodeGraphObject,
                curVersion);
    }

    public void setInfrastructureNodeEndVersion(String graphTag, String deploymentNodeName, String curVersion) {
        Result result = infrastructureNodesRepository.findInfrastructureNodesWithNullEndVersion(graphTag, deploymentNodeName);
        while (result.hasNext()) {
            String infrastructureNodeName = result.next().get("infrastructureNodeName").toString();
            infrastructureNodeName = infrastructureNodeName.substring(1, infrastructureNodeName.length() - 1);
            GraphObject infrastructureNodeGraphObject = new GraphObject("InfrastructureNode", "name", infrastructureNodeName);
            genericRepository.setObjectParameter(graphTag, infrastructureNodeGraphObject, "endVersion", curVersion);
        }
    }
}
