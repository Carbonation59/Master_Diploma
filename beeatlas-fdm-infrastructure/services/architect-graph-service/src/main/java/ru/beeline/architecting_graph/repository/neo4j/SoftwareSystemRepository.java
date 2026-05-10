/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.repository.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import ru.beeline.architecting_graph.model.GraphObject;
import ru.beeline.architecting_graph.model.SoftwareSystem;
import ru.beeline.architecting_graph.service.graph.Neo4jSessionManager;

import java.util.*;

@Slf4j
@Repository
public class SoftwareSystemRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result getSystem(String systemDSLIdentifier) {
        String query = "MATCH (n:SoftwareSystem {graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN n";
        Value parameters = Values.parameters("val1", systemDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getSystemById(Long id) {
        String query = "MATCH (d:SoftwareSystem) WHERE id(d) = $val1 RETURN d";
        Value parameters = Values.parameters("val1", id);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public Result getParentSystem(String containerDSLIdentifier) {
        String query = "MATCH (m:SoftwareSystem)-[r:Child]->(n:Container "
                + "{graphTag: \"Global\", structurizr_dsl_identifier: $val1}) RETURN m.structurizr_dsl_identifier";
        Value parameters = Values.parameters("val1", containerDSLIdentifier);
        return neo4jSessionManager.getSession().run(query, parameters);
    }
    public Result getParentSystemByDeploymentNodeId(Long id) {
        String query = "MATCH (parent:SoftwareSystem)-[r:Child]->(child:DeploymentNode)" +
                "WHERE id(child) = $val1 " +
                "RETURN parent";
        Value parameters = Values.parameters("val1", id);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public List<String> getInfluencingSystems(String cmdb) {
        String query =
                "MATCH (influencing:SoftwareSystem)-[r:Relationship]->(p:SoftwareSystem {graphTag: 'Global'}) " +
                        "WHERE toLower(p.cmdb) = toLower($cmdb) " +
                        "RETURN collect(influencing.cmdb) AS influencingSystems";
        return neo4jSessionManager.getSession().run(query, Values.parameters("cmdb", cmdb))
                .single()
                .get("influencingSystems")
                .asList(Value::asString);
    }

    public List<String> getDependentSystems(String cmdb) {
        String query =
                "MATCH (p:SoftwareSystem {graphTag: 'Global'}) " +
                        "WHERE toLower(p.cmdb) = toLower($cmdb) " +
                        "MATCH (p)-[r:Relationship]->(dependent:SoftwareSystem) " +
                        "RETURN collect(dependent.cmdb) AS dependentSystems";
        return neo4jSessionManager.getSession().run(query, Values.parameters("cmdb", cmdb))
                .single()
                .get("dependentSystems")
                .asList(Value::asString);
    }

    public Result getSoftwareSystem(String cmdb) {
        String query =
                "MATCH (softwareSystem:SoftwareSystem {graphTag: 'Global'}) " +
                        "WHERE toLower(softwareSystem.cmdb) = toLower($cmdb) " +
                        "RETURN softwareSystem";
        return neo4jSessionManager.getSession().run(query, Values.parameters("cmdb", cmdb));
    }

    public Set<Long> getContainerAndComponentChildIds(String cmdb) {
        String cypher = "MATCH (ss:SoftwareSystem {graphTag: 'Global', cmdb: $cmdb}) " +
                "OPTIONAL MATCH (ss)-[:Child]->(container:Container) " +
                "OPTIONAL MATCH (container)-[:Child]->(component:Component) " +
                "WITH collect(DISTINCT container) + collect(DISTINCT component) AS nodes " +
                "RETURN [node IN nodes | id(node)] AS nodeIds";

        Result result = neo4jSessionManager.getSession().run(cypher, Values.parameters("cmdb", cmdb));
        if (result.hasNext()) {
            return new HashSet<>(result.next().get("nodeIds").asList(Value::asLong));
        }
        return Collections.emptySet();
    }

    public HashSet<Map<String, Object>> getSoftwareSystemsFromRelationships(List<Long> sourceNodeIds) {
        String query =
                "MATCH (ss:SoftwareSystem) " +
                        "WHERE id(ss) IN $sourceNodeIds " +
                        "RETURN DISTINCT ss.cmdb AS name, id(ss) AS id";

        Result result = neo4jSessionManager.getSession().run(query, Values.parameters("sourceNodeIds", sourceNodeIds));
        HashSet<Map<String, Object>> softwareSystems = new HashSet<>();

        while (result.hasNext()) {
            Record record = result.next();
            Map<String, Object> system = new HashMap<>();
            system.put("name", record.get("name").asString());
            system.put("id", String.valueOf(record.get("id").asLong()));
            softwareSystems.add(system);
        }
        return softwareSystems;
    }

    public Map<Long, Map<String, String>> findParentSoftwareSystemsByContainers(Set<Long> sourceNodeIds) {
        if (sourceNodeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String cypher =
                "MATCH (ss:SoftwareSystem)-[:Child]->(c:Container) " +
                        "WHERE id(c) IN $sourceNodeIds " +
                        "RETURN id(c) AS containerId, id(ss) AS softwareSystemId, ss.cmdb AS softwareSystemCmdb";

        Result result = neo4jSessionManager.getSession().run(cypher, Values.parameters("sourceNodeIds", sourceNodeIds));

        Map<Long, Map<String, String>> containerToParentSoftwareSystem = new HashMap<>();
        while (result.hasNext()) {
            Record rec = result.next();
            Long containerId = rec.get("containerId").asLong();
            Map<String, String> parentSS = new HashMap<>();
            parentSS.put("id", String.valueOf(rec.get("softwareSystemId").asLong()));
            parentSS.put("name", rec.get("softwareSystemCmdb").asString());
            containerToParentSoftwareSystem.put(containerId, parentSS);
        }
        return containerToParentSoftwareSystem;
    }

    public Map<Long, Map<String, String>> getParentSoftwareSystemsForComponents(Set<Long> componentIds) {
        if (componentIds.isEmpty()) return Collections.emptyMap();

        String cypher =
                "MATCH (ss:SoftwareSystem)-[:Child]->(c:Container)-[:Child]->(comp:Component) " +
                        "WHERE id(comp) IN $componentIds " +
                        "RETURN id(comp) AS componentId, id(ss) AS softwareSystemId, ss.cmdb AS softwareSystemCmdb";

        Result result = neo4jSessionManager.getSession().run(cypher, Values.parameters("componentIds", componentIds));

        Map<Long, Map<String, String>> componentToParentSS = new HashMap<>();
        while (result.hasNext()) {
            Record rec = result.next();
            Long componentId = rec.get("componentId").asLong();
            Map<String, String> parentSS = new HashMap<>();
            parentSS.put("id", String.valueOf(rec.get("softwareSystemId").asLong()));
            parentSS.put("name", rec.get("softwareSystemCmdb").asString());
            componentToParentSS.put(componentId, parentSS);
        }
        return componentToParentSS;
    }

    public Result searchSoftwareSystemsByCMDBorName(String search) {
        String query =
                "MATCH (n:SoftwareSystem)" +
                        "WHERE toLower(n.graphTag) = toLower('Global')" +
                        "AND (toLower(n.cmdb) CONTAINS toLower($search) OR toLower(n.name) CONTAINS toLower($search))" +
                        "RETURN n";
        Value parameters = Values.parameters("search", search);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

    public boolean productExists(String cmdb) {
        String query = "MATCH (p:SoftwareSystem {graphTag: 'Global'}) " +
                "WHERE toLower(p.cmdb) = toLower($cmdb) " +
                "RETURN p LIMIT 1";
        Record productRecord = neo4jSessionManager.getSession().run(query, Values.parameters("cmdb", cmdb)).single();
        return productRecord != null;
    }

    public void updateSystemProperty(String graphTag, String cmdb, String key, Object value) {
        String setProperty = "MATCH (n:SoftwareSystem {graphTag: $graphTag1, cmdb: $cmdb1}) SET n." + key
                + " = $value";
        Value parameters = Values.parameters("graphTag1", graphTag, "cmdb1", cmdb, "value", value);
        neo4jSessionManager.getSession().run(setProperty, parameters);
    }

    public void setSystemParameters(String graphTag, String cmdb, SoftwareSystem softwareSystem,
                                    String version) {
        String setParameters = "MATCH (n:SoftwareSystem {graphTag: $graphTag1, cmdb: $cmdb1}) "
                + "SET n.name = $name1, n.description = $description1, n.tags = $tags1, n.url = $url1, "
                + "n.group = $group1, n.version = $version1";
        Value parameters = Values.parameters("graphTag1", graphTag, "cmdb1", cmdb, "name1",
                                             softwareSystem.getName(), "description1", softwareSystem.getDescription(), "tags1",
                                             softwareSystem.getTags(), "url1", softwareSystem.getUrl(), "group1",
                                             softwareSystem.getGroup(),
                                             "version1", version);
        neo4jSessionManager.getSession().run(setParameters, parameters);
    }

    public List<String> findDependentSystemsByContainerId(Long containerId) {
        String query =
                "MATCH (con:Container)-[r:Relationship ]->(parent:SoftwareSystem) " +
                        "WHERE id(con)=$containerId " +
                        "RETURN parent.cmdb";
        return neo4jSessionManager.getSession()
                .run(query, Values.parameters("containerId", containerId))
                .list(record -> record.get("parent.cmdb").asString());
    }

    public List<String> findDependentChildSystemsByComponent(Long componentId) {
        String query =
                "MATCH (com:Component)-[r:Relationship ]->(parent:SoftwareSystem) " +
                        "WHERE id(com)=$componentId " +
                        "RETURN parent";
        return neo4jSessionManager.getSession()
                .run(query, Values.parameters("componentId", componentId))
                .list(record -> record.get("parent.cmdb").asString());
    }
    public List<String> findDependentParentSystemsByComponent(Long componentId) {
        String query =
                "MATCH (parent:SoftwareSystem)-[r:Relationship ]->(com:Component) " +
                        "WHERE id(com)=$componentId" +
                        "RETURN parent";
        return neo4jSessionManager.getSession()
                .run(query, Values.parameters("componentId", componentId))
                .list(record -> record.get("parent.cmdb").asString());
    }

    public List<String> findInfluencingSystemsByNodeId(Long containerId) {
        String query =
                "MATCH (parent:SoftwareSystem )-[r:Relationship]->(con:Container) " +
                        "WHERE id(con)=$containerId " +
                        "RETURN parent";
        return  neo4jSessionManager.getSession()
                .run(query, Values.parameters("containerId", containerId))
                .list(record -> record.get("parent.cmdb").asString());
    }

    public Set<String> getDirectInfluencingSystems(List<Long> nodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (node:DeploymentNode) WHERE node.id IN nodeIds " +
                "OPTIONAL MATCH (ss_in:SoftwareSystem)-[:Relationship]->(node) " +
                "RETURN collect(DISTINCT ss_in.cmdb) AS influencingSystems";
        Value params = Values.parameters("ids", nodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);
        if (result.hasNext()) {
            return new HashSet<>(result.next().get("influencingSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> getDirectDependentSystems(List<Long> nodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (node:DeploymentNode) WHERE node.id IN nodeIds " +
                "OPTIONAL MATCH (node)-[:Relationship]->(ss_out:SoftwareSystem) " +
                "WITH collect(DISTINCT ss_out.cmdb) AS dependentSystems " +
                "RETURN apoc.coll.toSet(dependentSystems)";
        Value params = Values.parameters("ids", nodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);
        if (result.hasNext()) {
            return new HashSet<>(result.next().get("dependentSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> findInfluencingSystemsByDeploymentNodeIds(List<Long> nodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (src:DeploymentNode)-[:Relationship]->(node:DeploymentNode) " +
                "WHERE node.id IN nodeIds " +
                "OPTIONAL MATCH pathToSS = (ss:SoftwareSystem)-[:Child*0..]->(src) " +
                "WITH src, head(nodes(pathToSS)) AS ssParent " +
                "RETURN collect(DISTINCT ssParent.cmdb) AS influencingSystems";

        Value params = Values.parameters("ids", nodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);

        if (result.hasNext()) {
            return new HashSet<>(result.next().get("influencingSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> findDependentSystemsByDeploymentNodeIds(List<Long> nodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (node:DeploymentNode)-[:Relationship]->(dst:DeploymentNode) " +
                "WHERE node.id IN nodeIds " +
                "OPTIONAL MATCH pathToSS = (ss:SoftwareSystem)-[:Child*0..]->(dst) " +
                "WHERE all(i IN range(0, length(pathToSS)-2) WHERE (nodes(pathToSS)[i])-[:Child]->(nodes(pathToSS)[i+1])) " +
                "WITH dst, head(nodes(pathToSS)) AS ssParent " +
                "RETURN collect(DISTINCT ssParent.cmdb) AS dependentSystems";

        Value params = Values.parameters("ids", nodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);

        if (result.hasNext()) {
            return new HashSet<>(result.next().get("dependentSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> findDependentSystemsByDeploymentNodes(List<Long> nodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (d:DeploymentNode)-[:Child]->(ci:ContainerInstance) " +
                "WHERE d.id IN nodeIds " +
                "MATCH (c:Container)-[:Deploy]->(ci) " +
                "OPTIONAL MATCH (c)-[:Relationship]->(ss_dep:SoftwareSystem) " +
                "OPTIONAL MATCH (c)-[:Child]->(comp:Component) " +
                "OPTIONAL MATCH (comp)-[:Relationship]->(ss_comp_dep:SoftwareSystem) " +
                "WITH collect(DISTINCT ss_dep.cmdb) + collect(DISTINCT ss_comp_dep.cmdb) AS dependentSystems " +
                "RETURN apoc.coll.toSet(dependentSystems) AS dependentSystems";

        Value params = Values.parameters("ids", nodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);

        if (result.hasNext()) {
            return new HashSet<>(result.next().get("dependentSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> findInfluencingSystemsByDeploymentNodes(List<Long> nodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (d:DeploymentNode)-[:Child]->(ci:ContainerInstance) " +
                "WHERE d.id IN nodeIds " +
                "MATCH (c:Container)-[:Deploy]->(ci) " +
                "OPTIONAL MATCH (ss_inf:SoftwareSystem)-[:Relationship]->(c) " +
                "OPTIONAL MATCH (c)-[:Child]->(comp:Component) " +
                "OPTIONAL MATCH (ss_comp_inf:SoftwareSystem)-[:Relationship]->(comp) " +
                "WITH collect(DISTINCT ss_inf.cmdb) + collect(DISTINCT ss_comp_inf.cmdb) AS influencingSystems " +
                "RETURN apoc.coll.toSet(influencingSystems) AS influencingSystems";

        Value params = Values.parameters("ids", nodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);

        if (result.hasNext()) {
            return new HashSet<>(result.next().get("influencingSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> getInfrastructureInfluencingSystems(List<Long> deploymentNodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (d:DeploymentNode)-[:Child]->(infra:InfrastructureNode) " +
                "WHERE d.id IN nodeIds " +
                "MATCH (relNode)-[:Relationship]->(infra) " +
                "OPTIONAL MATCH (relNode:SoftwareSystem) " +
                "OPTIONAL MATCH (relNode:Container) " +
                "OPTIONAL MATCH (ssForContainer:SoftwareSystem)-[:Child]->(relNode) " +
                "OPTIONAL MATCH (relNode:Component) " +
                "OPTIONAL MATCH (cForComponent:Container)-[:Child]->(relNode) " +
                "OPTIONAL MATCH (ssForComponentContainer:SoftwareSystem)-[:Child]->(cForComponent) " +
                "OPTIONAL MATCH (relNode:DeploymentNode) " +
                "OPTIONAL MATCH path = (relNode)<-[:Child*0..]-(parentDN:DeploymentNode)<-[:Child]-(ssParent:SoftwareSystem) " +
                "WITH collect(DISTINCT CASE WHEN relNode:SoftwareSystem THEN relNode.cmdb ELSE NULL END) AS coll1, " +
                "collect(DISTINCT ssForContainer.cmdb) AS coll2, " +
                "collect(DISTINCT ssForComponentContainer.cmdb) AS coll3, " +
                "collect(DISTINCT ssParent.cmdb) AS coll4 " +
                "RETURN apoc.coll.toSet(coll1 + coll2 + coll3 + coll4) AS influencingSystems";

        Value params = Values.parameters("ids", deploymentNodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);

        if (result.hasNext()) {
            return new HashSet<>(result.next().get("influencingSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }

    public Set<String> getDependentSystemsForDeploymentNodes(List<Long> deploymentNodeIds) {
        String cypher = "WITH $ids AS nodeIds " +
                "MATCH (d:DeploymentNode)-[:Child]->(infra:InfrastructureNode) " +
                "WHERE d.id IN nodeIds " +
                "MATCH (infra)-[:Relationship]->(relNode) " +
                "OPTIONAL MATCH (relNode:SoftwareSystem) " +
                "WITH collect(DISTINCT relNode.cmdb) AS sysCmdb, relNode " +
                "OPTIONAL MATCH (relNode:Container) " +
                "OPTIONAL MATCH (ss:SoftwareSystem)-[:Child]->(relNode) " +
                "WITH relNode, collect(DISTINCT ss.cmdb) AS containerSysCmdb " +
                "OPTIONAL MATCH (comp:Component)-[:Child]->(c:Container) " +
                "OPTIONAL MATCH (ss2:SoftwareSystem)-[:Child]->(c) " +
                "WITH relNode, collect(DISTINCT ss2.cmdb) AS componentSysCmdb " +
                "OPTIONAL MATCH (dn:DeploymentNode) " +
                "WITH dn " +
                "MATCH (dn)<-[:Child*0..]-(parentDn:DeploymentNode)<-[:Child]-(ss:SoftwareSystem) " +
                "WITH collect(DISTINCT ss.cmdb) AS deploymentSysCmdb " +
                "WITH apoc.coll.toSet(sysCmdb + containerSysCmdb + componentSysCmdb + deploymentSysCmdb) AS dependentSystems " +
                "RETURN dependentSystems";

        Value params = Values.parameters("ids", deploymentNodeIds);
        Result result = neo4jSessionManager.getSession().run(cypher, params);
        if (result.hasNext()) {
            return new HashSet<>(result.next().get("dependentSystems").asList(Value::asString));
        }
        return Collections.emptySet();
    }
}
