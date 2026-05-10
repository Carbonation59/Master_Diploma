/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.repository.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.beeline.architecting_graph.model.Connection;
import ru.beeline.architecting_graph.model.RelationshipEntity;
import ru.beeline.architecting_graph.service.graph.Neo4jSessionManager;

import java.util.*;

@Slf4j
@Repository
public class RelationshipRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result getDeploymentNodeRelationships(String deploymentNodeDSLIdentifier) {
        String query = "MATCH (n:DeploymentNode {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Relationship]->(m) RETURN r, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", deploymentNodeDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getInfrastructureNodeRelationships(String infrastructureNodeDSLIdentifier) {
        String query = "MATCH (n:InfrastructureNode {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Relationship]->(m) RETURN r, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", infrastructureNodeDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainerInstanceRelationships(String containerInstanceDSLIdentifier) {
        String query = "MATCH (n:ContainerInstance {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Relationship]->(m) RETURN r, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", containerInstanceDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getDirectComponentRelationships(String componentDSLIndentifier) {
        String query = "MATCH (n:Component {graphTag: \"Global\", structurizr_dsl_identifier: $val1})" +
                "-[r:Relationship]->(m) RETURN r, m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", componentDSLIndentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getReverseComponentRelationships(String componentDSLIndentifier) {
        String query = "MATCH (m)-[r:Relationship]->(n:Component {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "RETURN r, m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", componentDSLIndentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getDirectContainerRelationships(String containerDSLIndentifier) {
        String query = "MATCH (n:Container {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Relationship]->(m) RETURN r, m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", containerDSLIndentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getReverseContainerRelationships(String containerDSLIndentifier) {
        String query = "MATCH (m)-[r:Relationship]->(n:Container {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + " RETURN r, m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", containerDSLIndentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getDirectSystemRelationships(String systemDSLIndentifier) {
        String query = "MATCH (n:SoftwareSystem {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Relationship]->(m) RETURN r, m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", systemDSLIndentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getComponentRelationshipsOut(String componentName, String cmdb) {
        String query = "MATCH (n:Component {graphTag: \"Global\", name: $val1})-[r:Relationship {sourceWorkspace: $cmdb}]->(m) "
                +
                "RETURN n, m, r.startVersion, r.endVersion, r.description";
        Value parameters = Values.parameters("val1", componentName, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getComponentRelationshipsIn(String componentName, String cmdb) {
        String query = "MATCH (m)-[r:Relationship {sourceWorkspace: $cmdb}]->(n:Component {graphTag: \"Global\", name: $val1}) "
                +
                "RETURN n, m, r.startVersion, r.endVersion, r.description";
        Value parameters = Values.parameters("val1", componentName, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getChildren(String parentType, String childType, String parentName) {
        String query = "MATCH (n:" + parentType + " {graphTag: \"Global\", name: $val1})-[r:Child]->(m:" + childType
                + ") " +
                "RETURN n, m, m.name, m.startVersion, m.endVersion";
        Value params = Values.parameters("val1", parentName);
        return neo4jSessionManager.getSession().run(query, params);
    }

    public Result getRelationships(String label, String name, String cmdb) {
        String query = "MATCH (n:" + label
                + " {graphTag: \"Global\", name: $val1})-[r:Relationship {sourceWorkspace: $cmdb}]->(m) " +
                "RETURN n, m, r.startVersion, r.endVersion, r.description";
        Value params = Values.parameters("cmdb", cmdb, "val1", name);
        return neo4jSessionManager.getSession().run(query, params);
    }

    public Result getRelationshipsByTagAndCmdb(String graphTag, String cmdb) {
        String getRelationships = "MATCH (n)-[r {sourceWorkspace: $cmdb, graphTag: $graphTag1}]->(m) "
                + "WHERE r.endVersion IS NULL RETURN n,m,r";
        Value parameters = Values.parameters("graphTag1", graphTag, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(getRelationships, parameters);
    }

    public Result getContainerRelationshipsOut(String containerName, String cmdb) {
        String query = "MATCH (n:Container {graphTag: \"Global\", name: $val1})-[r:Relationship {sourceWorkspace: $cmdb}]->(m) "
                +
                "RETURN n, m, r.startVersion, r.endVersion, r.description";
        Value parameters = Values.parameters("val1", containerName, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainerRelationshipsIn(String containerName, String cmdb) {
        String query = "MATCH (m)-[r:Relationship {sourceWorkspace: $cmdb}]->(n:Container {graphTag: \"Global\", name: $val1}) "
                +
                "RETURN n, m, r.startVersion, r.endVersion, r.description";
        Value parameters = Values.parameters("val1", containerName, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getSystemRelationshipsOut(String cmdb) {
        String query = """
                    MATCH (n:SoftwareSystem {graphTag: "Global", cmdb: $val1})
                    -[r:Relationship {sourceWorkspace: $cmdb}]->(m)
                    RETURN n, m, r.startVersion, r.endVersion, r.description
                """;
        Value parameters = Values.parameters("val1", cmdb, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getSystemRelationshipsIn(String cmdb) {
        String query = """
                    MATCH (m)-[r:Relationship {sourceWorkspace: $cmdb}]
                    ->(n:SoftwareSystem {graphTag: "Global", cmdb: $val1})
                    RETURN n, m, r.startVersion, r.endVersion, r.description
                """;
        Value parameters = Values.parameters("val1", cmdb, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getNodeRelationshipsOut(String label, String name, String cmdb) {
        String query = String.format(
                "MATCH (n:%s {graphTag: \"Global\", name: $val1})-[r:Relationship {sourceWorkspace: $cmdb}]->(m) " +
                        "RETURN n, m, r.startVersion, r.endVersion, r.description",
                label);
        Value parameters = Values.parameters("val1", name, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getNodeRelationshipsIn(String label, String name, String cmdb) {
        String query = String.format(
                "MATCH (m)-[r:Relationship {sourceWorkspace: $cmdb}]->(n:%s {graphTag: \"Global\", name: $val1}) " +
                        "RETURN n, m, r.startVersion, r.endVersion, r.description",
                label);
        Value parameters = Values.parameters("val1", name, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainerRelationships(String graphTag, String containerExternalName) {
        String findConnects = "MATCH (n:Container {graphTag: $graphTag1, external_name: $external_name1})-[r]-() "
                + "RETURN count(r) AS numberOfRelationships";
        Value parameters = Values.parameters("graphTag1", graphTag, "external_name1", containerExternalName);
        return neo4jSessionManager.getSession().run(findConnects, parameters);
    }

    public void createRelationshipQuery(String graphTag, RelationshipEntity relationship,
                                        Connection connection) {
        String cypher = "MATCH (a:" + connection.getSource().getType() + " {graphTag: $graphTag1, "
                + connection.getSource().getKey() + ": $value1}), (b:"
                + connection.getDestination().getType()
                + " {graphTag: $graphTag1, " + connection.getDestination().getKey() + ": $value2}) "
                + "CREATE (a)-[r:" + connection.getRelationshipType()
                + " {graphTag: $graphTag1, sourceWorkspace: $cmdb, description: $description1}]->(b) RETURN a, b";

        Value parameters = Values.parameters("graphTag1", graphTag, "value1", connection.getSource().getValue(),
                "value2", connection.getDestination().getValue(), "cmdb", connection.getCmdb(),
                "description1", relationship.getDescription());
        neo4jSessionManager.getSession().run(cypher, parameters);
    }

    public void setRelationshipProperty(String graphTag, String key, Object value,
                                        RelationshipEntity relationship,
                                        Connection connection) {
        String cypher = "MATCH (a:" + connection.getSource().getType() + " {graphTag: $graphTag1, "
                + connection.getSource().getKey()
                + ": $val1})" + "-[r:" + connection.getRelationshipType() +
                " {graphTag: $graphTag1, sourceWorkspace: $cmdb, description: $description1}]->" + "(b:"
                + connection.getDestination().getType()
                + " {graphTag: $graphTag1, " + connection.getDestination().getKey() + ": $val2}) "
                + "SET r." + key + " = $value";
        Value parameters = Values.parameters("graphTag1", graphTag, "val1", connection.getSource().getValue(),
                "val2", connection.getDestination().getValue(), "cmdb", connection.getCmdb(),
                "description1", relationship.getDescription(),
                "value", value);
        neo4jSessionManager.getSession().run(cypher, parameters);
    }

    public String getRelationshipParameter(String graphTag, String realtionshipDescription,
                                           Connection connection, String parameter) {
        String getParameter = "MATCH (a:" + connection.getSource().getType() + " {graphTag: $graphTag1, "
                + connection.getSource().getKey() + ": $val1})-[r:" + connection.getRelationshipType()
                + " {graphTag: $graphTag1, sourceWorkspace: $cmdb, description: $description1}]->(b:"
                + connection.getDestination().getType() + " {graphTag: $graphTag1, "
                + connection.getDestination().getKey() + ": $val2}) RETURN r." + parameter
                + " AS parameter";
        Value parameters = Values.parameters("graphTag1", graphTag, "val1",
                connection.getSource().getValue(), "cmdb", connection.getCmdb(), "description1",
                realtionshipDescription, "val2", connection.getDestination().getValue());
        Result result = neo4jSessionManager.getSession().run(getParameter, parameters);

        if (result.hasNext()) {
            Value paramValue = result.next().get("parameter");
            if (paramValue != null && !paramValue.isNull()) {
                return paramValue.toString();
            }
        }
        return "NULL";
    }

    public void setRelationshipParameter(String graphTag, String realtionshipDescription,
                                         Connection connection, String parameter, String value) {
        String setParameter = "MATCH (a:" + connection.getSource().getType() + " {graphTag: $graphTag1, "
                + connection.getSource().getKey() + ": $val1})-[r:" + connection.getRelationshipType()
                + " {graphTag: $graphTag1, sourceWorkspace: $cmdb, description: $description1}]->(b:"
                + connection.getDestination().getType() + " {graphTag: $graphTag1, "
                + connection.getDestination().getKey() + ": $val2}) SET r." + parameter
                + " = $parameter";
        Value parameters = Values.parameters("graphTag1", graphTag, "val1",
                connection.getSource().getValue(), "cmdb", connection.getCmdb(), "description1",
                realtionshipDescription, "val2", connection.getDestination().getValue(), "parameter",
                value);
        neo4jSessionManager.getSession().run(setParameter, parameters);
    }

    public Boolean checkIfRelationshipExists(String graphTag, RelationshipEntity relationship,
                                             Connection connection) {
        String query = "MATCH (a:" + connection.getSource().getType() + " {graphTag: $graphTag1, "
                + connection.getSource().getKey() + ": $value1})-[r:" + connection.getRelationshipType()
                + " {sourceWorkspace: $cmdb, description: $description1, graphTag: $graphTag1}]->(b:"
                + connection.getDestination().getType() + " {graphTag: $graphTag1, "
                + connection.getDestination().getKey()
                + ": $value2}) RETURN EXISTS((a)-->(b)) AS relationshipExists";
        Value parameters = Values.parameters(
                "graphTag1", graphTag
                , "value1", connection.getSource().getValue(),
                "value2", connection.getDestination().getValue()
                , "cmdb", connection.getCmdb(),
                "description1", relationship.getDescription());
        Result result = neo4jSessionManager.getSession().run(query, parameters);
        if (result.hasNext()) {
            return true;
        }
        return false;
    }

    public void buildSetRelationshipParameters(String graphTag, RelationshipEntity relationship,
                                               Connection connection) {
        String setParameters = "MATCH (a:" + connection.getSource().getType() + " {graphTag: $graphTag1, "
                + connection.getSource().getKey() + ": $val1})-[r:" + connection.getRelationshipType()
                + " {graphTag: $graphTag1, sourceWorkspace: $cmdb, description: $description1}]->(b:"
                + connection.getDestination().getType() + " {graphTag: $graphTag1, "
                + connection.getDestination().getKey()
                + ": $val2}) SET r.endVersion = $endVersion1, r.tags = $tags1,  "
                + "r.url = $url1, r.technology = $technology1, r.interactionStyle = $interactionStyle1, r.level = $level1";
        Value parameters = Values.parameters("graphTag1", graphTag, "val1",
                connection.getSource().getValue(), "cmdb", connection.getCmdb(), "description1",
                relationship.getDescription(), "val2", connection.getDestination().getValue(),
                "endVersion1", null,
                "tags1", relationship.getTags(), "url1", relationship.getUrl(), "technology1",
                relationship.getTechnology(), "interactionStyle1", relationship.getInteractionStyle(),
                "level1",
                connection.getLevel());
        neo4jSessionManager.getSession().run(setParameters, parameters);
    }

    public List<Map<String,Object>> getIncomingRelationships(Set<Long> nodeIds, Long destinationNodeId) {
        String query =
                "MATCH (src)-[rel:Relationship]->(target) " +
                        "WHERE id(target) IN $nodeIds " +
                        "RETURN id(rel) AS id, rel.description AS description, rel.technology AS technology, " +
                        "id(target) AS destinationId, id(src) AS sourceId";
        Result result = neo4jSessionManager.getSession().run(query, Values.parameters("nodeIds", nodeIds));
        List<Map<String,Object>> relationships = new ArrayList<>();
        while (result.hasNext()) {
            Record rec = result.next();
            Map<String,Object> map = new HashMap<>();
            map.put("id", String.valueOf(rec.get("id").asLong()));
            map.put("description", rec.get("description").asString(""));
            map.put("technology", rec.get("technology").asString(""));
            map.put("destinationId", String.valueOf(destinationNodeId));
            map.put("sourceId", String.valueOf(rec.get("sourceId").asLong()));
            relationships.add(map);
        }
        return relationships;
    }

    public List<Map<String,Object>> getOutgoingRelationships(Set<Long> nodeIds, Long sourceNodeId) {
        String query =
                "MATCH (src)-[rel:Relationship]->(target) " +
                        "WHERE id(src) IN $nodeIds " +
                        "RETURN id(rel) AS id, rel.description AS description, rel.technology AS technology, " +
                        "id(target) AS destinationId, id(src) AS sourceId";

        Result result = neo4jSessionManager.getSession().run(query, Values.parameters("nodeIds", nodeIds));
        List<Map<String,Object>> relationships = new ArrayList<>();
        while (result.hasNext()) {
            Record rec = result.next();
            Map<String,Object> map = new HashMap<>();
            map.put("id", String.valueOf(rec.get("id").asLong()));
            map.put("description", rec.get("description").asString(""));
            map.put("technology", rec.get("technology").asString(""));
            map.put("destinationId", String.valueOf(rec.get("destinationId").asLong()));
            map.put("sourceId", String.valueOf(sourceNodeId));
            relationships.add(map);
        }
        return relationships;
    }
}
