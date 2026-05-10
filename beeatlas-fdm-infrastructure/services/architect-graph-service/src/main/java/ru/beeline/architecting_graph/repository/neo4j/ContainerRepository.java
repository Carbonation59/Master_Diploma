/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.repository.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.beeline.architecting_graph.model.Container;
import ru.beeline.architecting_graph.model.GraphObject;
import ru.beeline.architecting_graph.service.graph.Neo4jSessionManager;

@Slf4j
@Repository
public class ContainerRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result checkIfContainerExists(String softwareSystemMnemonic, String containerMnemonic) {
        String query = "MATCH (a:SoftwareSystem {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Child]->(b:Container {graphTag: \"Global\", structurizr_dsl_identifier: $val2})  "
                + "WHERE r.graphTag = \"Global\" RETURN EXISTS((a)-->(b)) AS relationship_exists";
        Value parameters = Values.parameters("val1", softwareSystemMnemonic, "val2", containerMnemonic);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainer(String systemDSLIdentifier, String containerDSLIdentifier) {
        String query = "MATCH (n:SoftwareSystem {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Child]->(m:Container {graphTag: \"Global\", structurizr_dsl_identifier: $val2}) RETURN m";
        Value parameters = Values.parameters("val1", systemDSLIdentifier, "val2", containerDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainers(String systemDSLIdentifier) {
        String query = "MATCH (n:SoftwareSystem {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Child]->(m:Container) RETURN m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", systemDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainersByCmdb(String cmdb) {
        String query = "MATCH (n:SoftwareSystem {graphTag: \"Global\", cmdb: $val1})-[r:Child]->(m:Container) " +
                "RETURN n, m, m.name, m.startVersion, m.endVersion";
        Value parameters = Values.parameters("val1", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }
    public Result getParentContainer(String componentDSLIdentifier) {
        String query = "MATCH (m:Container)-[r:Child]->(n:Component "
                + "{graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", componentDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainerInstanceContainerId(String containerInstanceDSLIdentifier) {
        String query = "MATCH (n:Container)-[r:Deploy]->(m:ContainerInstance "
                + "{graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN n.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", containerInstanceDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public void executeSetContainerProperties(String graphTag, String containerName, String key,
                                              Object value) {
        String setProperties = "MATCH (n:Container {graphTag: $graphTag1, name: $name1}) SET n." + key
                + " = $value";
        Value parameters = Values.parameters("graphTag1", graphTag, "name1", containerName, "value", value);
        neo4jSessionManager.getSession().run(setProperties, parameters);
    }

    public void setContainerParameters(String graphTag, Container container,
                                       GraphObject containerGraphObject) {
        String setParameters = "MATCH (n:Container {graphTag: $graphTag1, " + containerGraphObject.getKey()
                + ": $value}) "
                + "SET n.description = $description1, n.technology = $technology1, n.tags = $tags1, "
                + "n.url = $url1, n.group = $group1, n.endVersion = $endVersion1";
        Value parameters = Values.parameters("graphTag1", graphTag, "value", containerGraphObject.getValue(),
                                             "description1", container.getDescription(), "technology1", container.getTechnology(),
                                             "tags1",
                                             container.getTags(), "url1", container.getUrl(), "group1", container.getGroup(),
                                             "endVersion1", null);
        neo4jSessionManager.getSession().run(setParameters, parameters);
    }

    public Result getContainersByTagAndCmdb(String graphTag, String cmdb) {
        String getContainers = "MATCH (n:SoftwareSystem {cmdb: $cmdb1, graphTag: $graphTag1})-[r:Child]->(m:Container) "
                + "WHERE m.endVersion IS NULL RETURN m.name AS containerName";
        Value parameters = Values.parameters("graphTag1", graphTag, "cmdb1", cmdb);
        return neo4jSessionManager.getSession().run(getContainers, parameters);
    }

    public Result findContainersWithParentCmdb(String search) {
        String query = "MATCH (cont:Container {graphTag: \"Global\"}) " +
                "WHERE toLower(cont.name) CONTAINS toLower($search) " +
                "MATCH (sys:SoftwareSystem)-[r:Child]->(cont) " +
                "RETURN cont.name as containerName, sys.cmdb as cmdb, cont.graphTag as graphTag";
        Value parameters = Values.parameters("search", search);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Long findContainerIdByParentSystemAndName(String name, String cmdb) {
        String query = "MATCH (parent:SoftwareSystem )-[r:Child]->(con:Container) \n" +
                "WHERE toLower(con.name) = toLower($name)  and toLower(parent.cmdb) = toLower($cmdb)\n" +
                "RETURN id(con) LIMIT 1";

        Result result =
                neo4jSessionManager.getSession().run(query, Values.parameters("cmdb", cmdb, "name", name));

        if (result.hasNext()) {
            return result.next().get("id(con)").asLong();
        } else {
            return null;
        }
    }
}

