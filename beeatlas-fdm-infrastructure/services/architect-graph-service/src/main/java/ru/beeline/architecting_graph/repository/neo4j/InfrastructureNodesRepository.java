/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.repository.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.beeline.architecting_graph.service.graph.Neo4jSessionManager;

@Slf4j
@Repository
public class InfrastructureNodesRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result getInfrastructureNodes(String deploymentNodeDSLIdentifier) {
        String query = "MATCH (n:DeploymentNode {graphTag: \"Global\", structurizr_dsl_identifier: $val1})"
                + "-[r:Child]->(m:InfrastructureNode) RETURN m, m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", deploymentNodeDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public void setInfrastructureNodeProperty(String graphTag, String nodeName,
                                              String propertyKey, Object propertyValue) {
        String query = "MATCH (n:InfrastructureNode {graphTag: $graphTag1, name: $name1}) SET n." + propertyKey
                + " = $value";
        Value parameters = Values.parameters("graphTag1", graphTag, "name1", nodeName, "value", propertyValue);
        neo4jSessionManager.getSession().run(query, parameters);
    }

    public void updateInfrastructureNode(String graphTag, String name,
                                         String description, String technology, String tags,
                                         String url, String endVersion) {
        String query = "MATCH (n:InfrastructureNode {graphTag: $graphTag1, name: $name1}) "
                + "SET n.description = $description1, n.technology = $technology1, n.tags = $tags1, "
                + "n.url = $url1, n.endVersion = $endVersion1";
        Value parameters = Values.parameters(
                "graphTag1", graphTag, "name1", name, "description1", description, "technology1",
                technology,
                "tags1", tags, "url1", url, "endVersion1", endVersion);
        neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result findInfrastructureNodesWithNullEndVersion(String graphTag,
                                                            String deploymentNodeName) {
        String cypher = "MATCH (n:DeploymentNode {name: $name1, graphTag: $graphTag1})-[r:Child]->(m:InfrastructureNode) "
                + "WHERE m.endVersion IS NULL RETURN m.name AS infrastructureNodeName";
        Value parameters = Values.parameters("graphTag1", graphTag, "name1", deploymentNodeName);
        return neo4jSessionManager.getSession().run(cypher, parameters);
    }

    public Result getInfrastructureNodesByDeploymentNodeId(Long deploymentNodeId) {
        String cypher = "MATCH (dn:DeploymentNode {graphTag: 'Global'})-[:Child]->(inf:InfrastructureNode)" +
                " where id(dn) = $dnId RETURN inf";
        return neo4jSessionManager.getSession().run(cypher, Values.parameters("dnId", deploymentNodeId));
    }
}
