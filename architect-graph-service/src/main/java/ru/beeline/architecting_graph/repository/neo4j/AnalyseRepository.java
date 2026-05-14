package ru.beeline.architecting_graph.repository.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.beeline.architecting_graph.service.graph.Neo4jSessionManager;

@Slf4j
@Repository
public class AnalyseRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result getElementLabels() {
        String query = "CALL db.labels() YIELD label RETURN label";
        return neo4jSessionManager.getSession().run(query);
    }

    public Result getRelationLabels() {
        String query = "CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType";
        return neo4jSessionManager.getSession().run(query);
    }

    public Result getPathSize(String nodeTypes, String relTypes, String nodeIdentifiers) {
        String param = "structurizr_dsl_identifier";
        String query = "WITH " + nodeIdentifiers + "AS nodeIds UNWIND nodeIds AS id MATCH (n {" + param + ": id}) "
                + "WITH collect(n) AS nodes WITH nodes, size(nodes) AS cnt UNWIND range(0, cnt-2) AS i "
                + "WITH nodes[i] AS startNode, nodes[i+1] AS endNode, i, cnt "
                + "MATCH pathSegment = shortestPath((startNode)-[" + relTypes + "*]->(endNode)) " + nodeTypes
                + "WITH i, pathSegment, cnt ORDER BY i WITH cnt, collect(pathSegment) AS segs WHERE size(segs) = cnt - 1 "
                + "WITH reduce(full = segs[0], i IN range(1, size(segs)-1) | apoc.path.combine(full, segs[i])) AS path "
                + "WHERE path IS NOT NULL RETURN length(path) AS pathLength";
        return neo4jSessionManager.getSession().run(query);
    }

    public Result findCycles(String graphTag, String nodeTypes, String relTypes, String nodeIdentifiers) {
        String query = "MATCH (n) " + nodeIdentifiers + "WITH collect(n) as nodes "
                + "CALL apoc.nodes.cycles(nodes" + relTypes + ") "
                + "YIELD path " + nodeTypes + graphTag + "RETURN path";

        return neo4jSessionManager.getSession().run(query);
    }

    public Result findSinglePoints(String graphTag, String nodeTypes, String relTypes, String nodeIdentifiers) {
        String query = "WITH randomUUID() AS uniqueName CALL gds.graph.project(uniqueName, " + nodeTypes + "{"
                + relTypes + "}) " + "YIELD graphName AS projName CALL gds.articulationPoints.stream(projName) "
                + "YIELD nodeId WITH projName, collect(nodeId) AS nodes CALL gds.graph.drop(projName) "
                + "YIELD graphName AS dropped UNWIND nodes AS nodeId WITH gds.util.asNode(nodeId) AS node " + graphTag
                + nodeIdentifiers + "RETURN node";

        return neo4jSessionManager.getSession().run(query);
    }

    public Result findGodElements(String graphTag, String nodeTypes, String relTypes, String nodeIdentifiers) {

        String percentile = "0.95";
        String query = "WITH randomUUID() AS graphName CALL gds.graph.project(graphName, " + nodeTypes + relTypes
                + "YIELD graphName AS projName CALL gds.degree.stream(projName, {orientation: 'UNDIRECTED'}) "
                + "YIELD nodeId, score AS totalDegree "
                + "WITH projName, collect({nodeId: nodeId, totalDegree: totalDegree}) AS nodes "
                + "WITH projName, nodes, apoc.coll.sort([n IN nodes | n.totalDegree]) AS sortedDegrees "
                + "WITH projName, nodes, sortedDegrees, size(nodes) AS totalNodes "
                + "WITH projName, nodes, "
                + "sortedDegrees[toInteger(ceil(" + percentile + " * totalNodes)) - 1] AS threshold "
                + "WITH projName, [n IN nodes WHERE n.totalDegree >= threshold] AS filteredNodes "
                + "UNWIND filteredNodes AS node "
                + "WITH projName, gds.util.asNode(node.nodeId) AS componentNode, node.totalDegree AS totalDegree "
                + graphTag + nodeIdentifiers
                + "WITH projName, collect({componentNode: componentNode, totalDegree: totalDegree}) AS result "
                + "CALL gds.graph.drop(projName) YIELD graphName AS dropped " + "UNWIND result AS row "
                + "RETURN row.componentNode AS component, row.totalDegree AS totalDegree";

        return neo4jSessionManager.getSession().run(query);
    }

    public Result findPathCapacity(String nodeTypes, String relTypes, String nodeIdentifiers) {
        String param = "structurizr_dsl_identifier";
        String nullRPS = "0.0";
        String fullCapacity = "node.rps * (1 - node.error_rate) / node.latency"; // минимальный
        String nullErrRateCapacity = "node.rps / node.latency"; // сумма
        String nullLatencyCapacity = "node.rps * (1 - node.error_rate)";
        String notNullCapacity = "node.rps";
        String nullErrRate = "1.0"; // 1 - err и перемножаем
        String resErrRate = "exp(sum(log(nodeErrorFactor)))";
        String query = "WITH " + nodeIdentifiers + "AS nodeIds UNWIND nodeIds AS id MATCH (n {" + param + ": id}) "
                + "WITH collect(n) AS nodes WITH nodes, size(nodes) AS cnt UNWIND range(0, cnt-2) AS i "
                + "WITH nodes[i] AS startNode, nodes[i+1] AS endNode, i, cnt "
                + "MATCH pathSegment = shortestPath((startNode)-[" + relTypes + "*]->(endNode)) " + nodeTypes
                + "WITH i, pathSegment, cnt ORDER BY i WITH cnt, collect(pathSegment) AS segs WHERE size(segs) = cnt - 1 "
                + "WITH reduce(full = segs[0], i IN range(1, size(segs)-1) | apoc.path.combine(full, segs[i])) AS path "
                + "WHERE path IS NOT NULL WITH path, nodes(path) AS pathNodes UNWIND pathNodes AS node "
                + "WITH path, CASE WHEN node.rps IS NULL THEN " + nullRPS + " ELSE CASE "
                + "WHEN node.latency IS NOT NULL AND node.latency <> 0 AND node.error_rate IS NOT NULL "
                + "THEN " + fullCapacity + " "
                + "WHEN node.latency IS NOT NULL AND node.latency <> 0 THEN " + nullErrRateCapacity + " "
                + "WHEN node.error_rate IS NOT NULL THEN " + nullLatencyCapacity + " ELSE " + notNullCapacity + " END "
                + "END AS nodeThroughput, CASE WHEN node.error_rate IS NOT NULL THEN node.error_rate ELSE "
                + nullErrRate + " END AS nodeErrorFactor "
                + "WITH path, min(nodeThroughput) AS throughput, " + resErrRate + " AS error_rate "
                + "RETURN path, throughput, error_rate";

        return neo4jSessionManager.getSession().run(query);
    }

    public Result findCriticalInfrastructure(String nodeIdentifiers) {
        String query = "WITH " + nodeIdentifiers + "AS systemIds "
                + "MATCH (system) WHERE system.structurizr_dsl_identifier IN systemIds "
                + "WITH collect(system) AS systems MATCH (d:DeploymentNode)"
                + "MATCH (s)-[:Child*1..]->(d) WHERE s IN systems WITH d, systems, count(DISTINCT s) AS matched "
                + "WHERE matched = size(systems) RETURN d AS component";
        return neo4jSessionManager.getSession().run(query);
    }

    public Result findPerimeterViolation(String deploymentNodeIdentifier, String properties) {
        String query = "MATCH (root:DeploymentNode) "
                + "WHERE root.structurizr_dsl_identifier = " + deploymentNodeIdentifier
                + "MATCH (root)-[:Child*]->(descendant) "
                + "MATCH (elem)-[:Deploy]->(descendant) "
                + "WHERE (elem.technology IS NULL) " + properties
                + "RETURN DISTINCT elem AS component ";

        return neo4jSessionManager.getSession().run(query);
    }

    // добавить генерацию отчёта

}
