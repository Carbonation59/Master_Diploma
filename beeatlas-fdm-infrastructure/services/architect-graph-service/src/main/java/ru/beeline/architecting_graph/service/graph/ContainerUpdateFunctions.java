/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.repository.neo4j.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ContainerUpdateFunctions {

    @Autowired
    DeploymentNodeUpdateFunctions deploymentNodeUpdateFunctions;

    @Autowired
    ComponentUpdateService componentUpdateService;

    @Autowired
    GenericRepository genericRepository;

    @Autowired
    CreateExternalObjects createExternalObjects;

    @Autowired
    ContainerRepository containerRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    @Autowired
    SoftwareSystemRepository softwareSystemRepository;
    @Autowired
    private ComponentRepository componentRepository;

    public void createGraph(String graphTag, Workspace workspace) throws Exception {
        log.info("createGraph 1");
        Model model = workspace.getModel();
        String cmdb = model.getProperties().get("workspace_cmdb").toString();
        SoftwareSystem softwareSystem = getSoftwareSystem(model, cmdb);
        HashMap<String, GraphObject> objects = new HashMap<>();
        String curVersion = null;
        log.info("createGraph 2");
        if (graphTag.equals("Global")) {
            curVersion = updateSystem(graphTag, softwareSystem, cmdb, objects);
            Integer prevVersion = Integer.parseInt(curVersion) - 1;
            setEndVersion(graphTag, cmdb, prevVersion.toString());
        } else {
            graphTag = "Local " + cmdb;
            genericRepository.deleteGraph(graphTag);
            updateSystem(graphTag, softwareSystem, cmdb, objects);
        }
        log.info("createGraph 4");
        log.info("createGraph 4.1");
        updateContainers(graphTag, model, softwareSystem, cmdb, curVersion, objects);
        log.info("createGraph 4.2");
        updateSystemRelationships(graphTag, model, cmdb, curVersion,
                objects);
        log.info("createGraph 4.3");
        updateDeploymentNodes(graphTag, model, softwareSystem.getId(),
                cmdb, curVersion, objects);
        log.info("createGraph 5");
        if (model.getDeploymentNodes() != null) {
            for (DeploymentNode deploymentNode : model.getDeploymentNodes()) {
                deploymentNodeUpdateFunctions.updateDeploymentNodeRelationships(graphTag, deploymentNode,
                        curVersion, cmdb, model, objects);
            }
        }
    }

    public SoftwareSystem getSoftwareSystem(Model model, String cmdb) {
        for (SoftwareSystem softwareSystem : model.getSoftwareSystems()) {
            if (softwareSystem.getProperties() != null && softwareSystem.getProperties().get("cmdb") != null
                    && softwareSystem.getProperties().get("cmdb").equals(cmdb)) {
                return softwareSystem;
            }
        }
        return null;
    }

    public String updateSystem(String graphTag, SoftwareSystem softwareSystem, String cmdb,
                               HashMap<String, GraphObject> objects) {
        GraphObject systemGraphObject = new GraphObject("SoftwareSystem", "cmdb", cmdb);
        boolean exists = genericRepository.checkIfObjectExists(graphTag, systemGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, systemGraphObject);
        }
        objects.put(softwareSystem.getId(), systemGraphObject);
        return setParametersForSystem(graphTag, softwareSystem, cmdb, systemGraphObject);
    }

    public String setParametersForSystem(String graphTag, SoftwareSystem softwareSystem, String cmdb,
                                         GraphObject systemGraphObject) {
        String version = null;
        if (graphTag.equals("Global")) {
            version = getSystemVersion(graphTag, systemGraphObject).toString();
        }
        softwareSystemRepository.setSystemParameters(graphTag, cmdb, softwareSystem, version);
        setSystemProperties(graphTag, softwareSystem, cmdb);
        return version;
    }

    public void setParametersForContainer(String graphTag, Container container,
                                          GraphObject containerGraphObject, String curVersion) {
        if (graphTag.equals("Global")
                && genericRepository.getObjectParameter(graphTag, containerGraphObject, "startVersion")
                .toString().equals("NULL")) {
            genericRepository.setObjectParameter(graphTag, containerGraphObject, "startVersion", curVersion);
        }
        containerRepository.setContainerParameters(graphTag, container, containerGraphObject);
        setContainerProperties(graphTag, container);
    }

    public void setContainerProperties(String graphTag, Container container) {
        if (container.getProperties() != null) {
            for (Map.Entry<String, Object> entry : container.getProperties().entrySet()) {
                String key = entry.getKey();
                key = key.replaceAll("[^a-zA-Z0-9]", "_");
                containerRepository.executeSetContainerProperties(graphTag, container.getName(), key,
                                                                  entry.getValue());
            }
        }
    }

    public Integer getContainerNumberOfConnects(String graphTag, String containerExternalName) {
        Result result = relationshipRepository.getContainerRelationships(graphTag, containerExternalName);
        String numberOfRelationships = result.next().get("numberOfRelationships").toString();
        if (numberOfRelationships.equals("NULL")) {
            return 0;
        }
        return Integer.parseInt(numberOfRelationships);
    }

    public Boolean needContainerReplace(String graphTag, Container container, GraphObject externalContainerGraphObject) {
        Integer numberOfRelationshipsFirst = getContainerNumberOfConnects(graphTag,
                externalContainerGraphObject.getValue());
        String source = genericRepository.getObjectParameter(graphTag, externalContainerGraphObject, "source")
                .toString();
        Integer numberOfRelationshipsSecond = 0;
        if (container.getRelationships() != null) {
            numberOfRelationshipsSecond = container.getRelationships().size();
        }
        if (container.getComponents() != null) {
            numberOfRelationshipsSecond = numberOfRelationshipsSecond + container.getComponents().size();
        }
        if (numberOfRelationshipsFirst >= numberOfRelationshipsSecond || source.equals("\"landscape\"")) {
            return false;
        }
        return true;
    }

    public Boolean changeContainer(String graphTag, Container container, GraphObject externalContainerGraphObject,
                                   String curVersion, HashMap<String, GraphObject> objects) {
        String startVersion = genericRepository
                .getObjectParameter(graphTag, externalContainerGraphObject, "startVersion").toString();
        if (startVersion.equals("NULL")) {
            genericRepository.setObjectParameter(graphTag, externalContainerGraphObject, "name",
                                                 container.getName());
            return true;
        }
        String endVersion = genericRepository
                .getObjectParameter(graphTag, externalContainerGraphObject, "endVersion").toString();
        if (endVersion.equals("NULL")) {
            return needContainerReplace(graphTag, container, externalContainerGraphObject);
        }
        return true;
    }

    public void updateContainer(String graphTag, Container container, String curVersion,
                                HashMap<String, GraphObject> objects) {
        boolean externalExists = false;
        GraphObject externalContainerGraphObject = null;
        if (container.getProperties() != null && container.getProperties().containsKey("external_name")
                && container.getProperties().get("external_name") != null) {
            String containerExternalName = container.getProperties().get("external_name").toString();
            externalContainerGraphObject = new GraphObject("Container", "external_name",
                    containerExternalName);
            externalExists = genericRepository.checkIfObjectExists(graphTag, externalContainerGraphObject);
        }
        if (externalExists) {
            if (changeContainer(graphTag, container, externalContainerGraphObject, curVersion, objects)) {
                genericRepository.setObjectParameter(graphTag, externalContainerGraphObject, "external_name",
                                                     null);
            } else {
                container.getProperties().put("external_name", null);
            }
        }
        GraphObject containerGraphObject = new GraphObject("Container", "name", container.getName());
        boolean exists = genericRepository.checkIfObjectExists(graphTag, containerGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, containerGraphObject);
        }
        objects.put(container.getId(), containerGraphObject);
        setParametersForContainer(graphTag, container, containerGraphObject, curVersion);
    }

    public void updateContainers(String graphTag, Model model, SoftwareSystem softwareSystem,
                                 String cmdb, String curVersion, HashMap<String, GraphObject> objects) {
        if (softwareSystem.getContainers() != null) {
            for (Container container : softwareSystem.getContainers()) {
                container.setName(container.getName() + "~" + cmdb.toString());
                String containerExternalName = cmdb;
                if (container.getProperties() != null && container.getProperties().containsKey("external_name")
                        && container.getProperties().get("external_name") != null) {
                    containerExternalName = container.getProperties().get("external_name").toString() + "."
                            + cmdb;
                    container.getProperties().put("external_name", containerExternalName);
                }
                updateContainer(graphTag, container, curVersion, objects);
                createExternalObjects.updateChildRelationship(graphTag, model, curVersion,
                        softwareSystem.getId(), container.getId(), cmdb, objects);
                componentUpdateService.updateComponents(graphTag, model, container, cmdb, curVersion,
                        containerExternalName, objects);
            }
        }
    }

    public void setContainerEndVersion(String graphTag, String cmdb, String curVersion) {
        Result result = containerRepository.getContainersByTagAndCmdb(graphTag, cmdb);
        while (result.hasNext()) {
            String containerName = result.next().get("containerName").toString();
            containerName = containerName.substring(1, containerName.length() - 1);

            GraphObject containerGraphObject = new GraphObject("Container", "name", containerName);
            genericRepository.setObjectParameter(graphTag, containerGraphObject, "endVersion", curVersion);
            setComponentEndVersion(graphTag, containerName, cmdb, curVersion);
        }
    }

    public void setComponentEndVersion(String graphTag, String containerName, String cmdb, String curVersion) {
        Result result = componentRepository.findComponentNamesWithNullEndVersion(graphTag, containerName);
        while (result.hasNext()) {
            String rawName = result.next().get("componentName").toString();
            String cleanedName = rawName.substring(1, rawName.length() - 1);
            GraphObject componentGraphObject = new GraphObject("Component", "name", cleanedName);
            genericRepository.setObjectParameter(graphTag, componentGraphObject, "endVersion", curVersion);
        }
    }

    public void updateContainerRelationships(String graphTag, Model model, SoftwareSystem softwareSystem,
                                             String cmdb, String curVersion, HashMap<String, GraphObject> objects) {
        if (softwareSystem.getContainers() != null) {
            for (Container container : softwareSystem.getContainers()) {
                if (container.getRelationships() != null) {
                    for (RelationshipEntity relationship : container.getRelationships()) {
                        if (relationship.getLinkedRelationshipId() == null) {
                            createExternalObjects.updateDefaultRelationship(graphTag, relationship,
                                    model, curVersion, cmdb, "C2", objects);
                        }
                    }
                }
                componentUpdateService.updateComponentRelationships(graphTag, model, container, cmdb,
                        curVersion, objects);
            }
        }
    }

    public void setEndVersion(String graphTag, String cmdb, String curVersion) {
        setContainerEndVersion(graphTag, cmdb, curVersion);
        Result result = deploymentNodesRepository.getDeploymentNodeNames(graphTag, cmdb);
        while (result.hasNext()) {
            String deploymentNodeName = result.next().get("deploymentNodeName").toString();
            deploymentNodeName = deploymentNodeName.substring(1, deploymentNodeName.length() - 1);
            deploymentNodeUpdateFunctions.setDeploymentNodeEndVersion(graphTag, deploymentNodeName,
                    curVersion, cmdb);
        }
        setRelationshipsEndVersion(graphTag, curVersion, cmdb);
    }

    public GraphObject getGraphObject(Value graphNode) {
        String label = graphNode.asNode().labels().toString();
        String type = label.substring(1, label.length() - 1);
        String key = "name";
        if (type.equals("SoftwareSystem")) {
            key = "cmdb";
        }
        Object value = graphNode.asNode().asMap().get(key);
        if (value == null) {
            value = graphNode.asNode().asMap().get("external_name");
        }
        return new GraphObject(type, key, value.toString());
    }

    public void setRelationshipsEndVersion(String graphTag, String curVersion, String cmdb) {
        Result result = relationshipRepository.getRelationshipsByTagAndCmdb(graphTag, cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Connection connection = new Connection();
            connection.setSource(getGraphObject(record.get("n")));
            connection.setDestination(getGraphObject(record.get("m")));
            connection.setCmdb(cmdb);
            Value connectValue = record.get("r");
            connection.setRelationshipType(connectValue.asRelationship().type().toString());
            String relationshipDescription = connectValue.asRelationship().get("description").toString();
            relationshipDescription = relationshipDescription.substring(1, relationshipDescription.length() - 1);
            relationshipRepository.setRelationshipParameter(graphTag, relationshipDescription, connection,
                                                            "endVersion", curVersion);
        }
    }

    public void updateDeploymentNodes(String graphTag, Model model, String softwareSystemId,
                                      String cmdb, String curVersion, HashMap<String, GraphObject> objects) {
        if (model.getDeploymentNodes() != null) {
            for (DeploymentNode deploymentNode : model.getDeploymentNodes()) {
                deploymentNode.setName(deploymentNode.getName() + "~" + cmdb.toString());
                deploymentNodeUpdateFunctions.updateDeploymentNode(graphTag, deploymentNode, curVersion, cmdb,
                        model,
                        objects);
                createExternalObjects.updateChildRelationship(graphTag, model,
                        curVersion, softwareSystemId, deploymentNode.getId(), cmdb, objects);
            }
        }
    }

    public Integer getSystemVersion(String graphTag, GraphObject systemGraphObject) {
        String version = genericRepository.getObjectParameter(graphTag, systemGraphObject, "version").toString();
        if (version.equals("NULL")) {
            version = "0";
            genericRepository.setObjectParameter(graphTag, systemGraphObject, "version", "0");
        } else {
            version = version.substring(1, version.length() - 1);
        }
        return (Integer.parseInt(version) + 1);
    }

    public void setSystemProperties(String graphTag, SoftwareSystem softwareSystem, String cmdb) {
        if (softwareSystem.getProperties() != null) {
            for (Map.Entry<String, Object> entry : softwareSystem.getProperties().entrySet()) {
                String key = entry.getKey();
                key = key.replaceAll("[^a-zA-Z0-9]", "_");
                softwareSystemRepository.updateSystemProperty(graphTag, cmdb, key, entry.getValue());
            }
        }
    }

    public void updateSystemRelationships(String graphTag, Model model, String cmdb,
                                          String curVersion, HashMap<String, GraphObject> objects) {
        for (SoftwareSystem softwareSystem : model.getSoftwareSystems()) {
            if (softwareSystem.getRelationships() != null) {
                for (RelationshipEntity relationship : softwareSystem.getRelationships()) {
                    if (relationship.getLinkedRelationshipId() == null) {
                        createExternalObjects.updateDefaultRelationship(graphTag, relationship, model,
                                curVersion, cmdb, "C1", objects);
                    }
                }
            }
            updateContainerRelationships(graphTag, model, softwareSystem, cmdb, curVersion, objects);
        }
    }
}
