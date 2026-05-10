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
public class EnvironmentRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result checkIfEnvironmentExists(String environment) {
        String query = "MATCH (n:Environment {name: $val1}) RETURN n";
        Value parameters = Values.parameters("val1", environment);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getInfrastructureNodeEnvironment(String infrastructureNodeDSLIdentifier) {
        String query = "MATCH (n:Environment)-[r:Child]->(m:InfrastructureNode "
                + "{graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN n.name";
        Value parameters = Values.parameters("val1", infrastructureNodeDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getContainerInstanceEnvironment(String containerInstanceDSLIdentifier) {
        String query = "MATCH (n:Environment)-[r:Child]->(m:ContainerInstance "
                + "{graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN n.name";
        Value parameters = Values.parameters("val1", containerInstanceDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getDeploymentNodeEnvironment(String deploymentNodeDSLIdentifier) {
        String query = "MATCH (n:Environment)-[r:Child]->(m:DeploymentNode "
                + "{graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN n.name";
        Value parameters = Values.parameters("val1", deploymentNodeDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getDeploymentNodeEnvironmentNameByIdChild(Long id) {
        String query = "MATCH (parent:Environment)-[:Child]->(child:DeploymentNode) "
                + "WHERE id(child) = $val1 RETURN parent.name";
        Value parameters = Values.parameters("val1", id);
        return neo4jSessionManager.getSession().run(query, parameters);
    }
}
