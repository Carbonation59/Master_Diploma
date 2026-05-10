/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.model.*;
import ru.beeline.architecting_graph.repository.neo4j.GenericRepository;
import ru.beeline.architecting_graph.repository.neo4j.RelationshipRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class CreateExternalObjects {

    @Autowired
    GenericRepository genericRepository;
    @Autowired
    private RelationshipRepository relationshipRepository;

    public void createExternalSystem(String graphTag, SoftwareSystem softwareSystem,
                                     String cmdb, HashMap<String, GraphObject> objects) {
        GraphObject systemGraphObject = new GraphObject("SoftwareSystem", "cmdb", cmdb);
        boolean exists = genericRepository.checkIfObjectExists(graphTag, systemGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, systemGraphObject);
            genericRepository.setObjectParameter(graphTag, systemGraphObject, "name", softwareSystem.getName());
            if (softwareSystem.getProperties() != null
                    && softwareSystem.getProperties().containsKey("structurizr.dsl.identifier")) {
                genericRepository.setObjectParameter(graphTag, systemGraphObject, "structurizr_dsl_identifier",
                                                     softwareSystem.getProperties().get("structurizr.dsl.identifier").toString());
            }
        }
        objects.put(softwareSystem.getId(), systemGraphObject);
    }

    public void createExternalContainer(String graphTag, Container container,
                                        String containerExternalName, HashMap<String, GraphObject> objects) {
        GraphObject containerGraphObject = new GraphObject("Container", "external_name",
                containerExternalName);
        Boolean exists = genericRepository.checkIfObjectExists(graphTag, containerGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, containerGraphObject);
            genericRepository.setObjectParameter(graphTag, containerGraphObject, "name", container.getName());
            if (container.getProperties() != null
                    && container.getProperties().containsKey("structurizr.dsl.identifier")) {
                genericRepository.setObjectParameter(graphTag, containerGraphObject,
                                                     "structurizr_dsl_identifier",
                                                     container.getProperties().get("structurizr.dsl.identifier").toString());
            }
        }
        objects.put(container.getId(), containerGraphObject);
    }

    public void createExternalComponent(String graphTag, Component component,
                                        String componentExternalName, HashMap<String, GraphObject> objects) {
        GraphObject componentGraphObject = new GraphObject("Component", "external_name",
                componentExternalName);
        Boolean exists = genericRepository.checkIfObjectExists(graphTag, componentGraphObject);
        if (!exists) {
            genericRepository.createObject(graphTag, componentGraphObject);
            genericRepository.setObjectParameter(graphTag, componentGraphObject, "name", component.getName());

            if (component.getProperties() != null
                    && component.getProperties().containsKey("structurizr.dsl.identifier")) {
                genericRepository.setObjectParameter(graphTag, componentGraphObject,
                                                     "structurizr_dsl_identifier",
                                                     component.getProperties().get("structurizr.dsl.identifier").toString());
            }
        }
        objects.put(component.getId(), componentGraphObject);
    }

    public void createExternalObject(String graphTag, Model model, String curVersion,
                                     String objectId, HashMap<String, GraphObject> objects) {
        for (SoftwareSystem softwareSystem : model.getSoftwareSystems()) {
            if (softwareSystem.getProperties() == null || !softwareSystem.getProperties().containsKey("cmdb")) {
                continue;
            }
            String cmdb = softwareSystem.getProperties().get("cmdb").toString();
            if (objectId.equals(softwareSystem.getId())) {
                createExternalSystem(graphTag, softwareSystem, cmdb, objects);
                return;
            }
            if (softwareSystem.getContainers() != null) {
                for (Container container : softwareSystem.getContainers()) {
                    String containerExternalName = cmdb;
                    if (container.getProperties() != null && container.getProperties().containsKey("external_name")
                            && container.getProperties().get("external_name") != null) {
                        containerExternalName = container.getProperties().get("external_name").toString() + "." + cmdb;
                    }
                    if (objectId.equals(container.getId())) {
                        createExternalContainer(graphTag, container, containerExternalName, objects);
                        updateChildRelationship(graphTag, model, curVersion,
                                softwareSystem.getId(), container.getId(), cmdb, objects);
                        return;
                    }
                    if (container.getComponents() != null) {
                        for (Component component : container.getComponents()) {
                            if (objectId.equals(component.getId())) {
                                String componentExternalName = containerExternalName;
                                if (component.getProperties() != null
                                        && component.getProperties().containsKey("external_name")
                                        && component.getProperties().get("external_name") != null) {
                                    componentExternalName = component.getProperties().get("external_name").toString()
                                            + "." + containerExternalName;
                                }
                                createExternalComponent(graphTag, component, componentExternalName,
                                        objects);
                                updateChildRelationship(graphTag, model,
                                        curVersion, container.getId(), component.getId(), cmdb, objects);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public void setRelationshipProperties(String graphTag, RelationshipEntity relationship, Connection connection) {
        if (relationship.getProperties() != null) {
            for (Map.Entry<String, Object> entry : relationship.getProperties().entrySet()) {
                String sanitizedKey = entry.getKey().replaceAll("[^a-zA-Z0-9]", "_");
                relationshipRepository.setRelationshipProperty(graphTag, sanitizedKey, entry.getValue(), relationship, connection);
            }
        }
    }

    public void setRelationshipNumberOfConnects(String graphTag, RelationshipEntity relationship,
                                                Connection connection) {
        if (relationship.getDescription().equals("None")) {
            String numberOfConnects = relationshipRepository.getRelationshipParameter(graphTag, "None", connection,
                                                                                      "numberOfConnects").toString();
            String endVersionRelationship = relationshipRepository.getRelationshipParameter(graphTag, "None", connection,
                                                                                            "endVersion").toString();
            if (numberOfConnects.equals("NULL") || !endVersionRelationship.equals("NULL")) {
                numberOfConnects = "0";
            } else {
                numberOfConnects = numberOfConnects.substring(1, numberOfConnects.length() - 1);
            }
            Integer newNumberOfConnects = Integer.parseInt(numberOfConnects) + 1;
            relationshipRepository.setRelationshipParameter(graphTag, "None", connection, "numberOfConnects",
                                                            newNumberOfConnects.toString());
        }
    }

    public void setParametersForRelationship(String graphTag, RelationshipEntity relationship,
                                             Connection connection, String curVersion) {
        if (graphTag.equals("Global") && relationshipRepository.getRelationshipParameter(graphTag, relationship.getDescription(),
                                                                                         connection, "startVersion").toString().equals("NULL")) {
            relationshipRepository.setRelationshipParameter(graphTag, relationship.getDescription(), connection, "startVersion",
                                                            curVersion);
        }
        setRelationshipNumberOfConnects(graphTag, relationship, connection);
        relationshipRepository.buildSetRelationshipParameters(graphTag, relationship, connection);
        setRelationshipProperties(graphTag, relationship, connection);
    }

    public void updateChildRelationship(String graphTag, Model model, String curVersion,
                                        String sourceId, String destinationId, String cmdb, HashMap<String, GraphObject> objects) {
        updateRelationship(graphTag,
                           RelationshipEntity.builder().sourceId(sourceId).destinationId(destinationId).description("Child").build(),
                           model,
                           curVersion,
                           Connection.builder().relationshipType("Child").cmdb(cmdb).build(),
                           objects);
    }

    public void updateDeployRelationship(String graphTag, Model model, String curVersion,
                                         String sourceId, String destinationId, String cmdb, HashMap<String, GraphObject> objects) {

        RelationshipEntity relationship = new RelationshipEntity();
        relationship.setSourceId(sourceId);
        relationship.setDestinationId(destinationId);
        relationship.setDescription("Deploy");
        Connection connection = new Connection();
        connection.setRelationshipType("Deploy");
        connection.setCmdb(cmdb);
        updateRelationship(graphTag, relationship, model, curVersion, connection, objects);
    }

    public void updateDefaultRelationship(String graphTag, RelationshipEntity relationship, Model model,
                                          String curVersion, String cmdb, String level, HashMap<String, GraphObject> objects) {
        updateRelationship(graphTag,
                           relationship,
                           model,
                           curVersion,
                           Connection.builder().cmdb(cmdb).level(level).relationshipType("Relationship").build(),
                           objects);
    }

    public void updateRelationship(String graphTag, RelationshipEntity relationship, Model model,
                                   String curVersion, Connection connection, HashMap<String, GraphObject> objects) {
        if (!objects.containsKey(relationship.getSourceId())) {
            createExternalObject(graphTag, model, curVersion, relationship.getSourceId(),
                    objects);
        }
        GraphObject source = objects.get(relationship.getSourceId());
        if (source == null) {
            return;
        }
        if (!objects.containsKey(relationship.getDestinationId())) {
            createExternalObject(graphTag, model, curVersion,
                    relationship.getDestinationId(), objects);
        }
        GraphObject destination = objects.get(relationship.getDestinationId());
        if (destination == null) {
            return;
        }
        connection.setSource(source);
        connection.setDestination(destination);
        if (relationship.getDescription() == null) {
            relationship.setDescription("None");
        }
        if (!relationshipRepository.checkIfRelationshipExists(graphTag, relationship, connection)) {
            relationshipRepository.createRelationshipQuery(graphTag, relationship, connection);
        }
        setParametersForRelationship(graphTag, relationship, connection, curVersion);
    }
}
