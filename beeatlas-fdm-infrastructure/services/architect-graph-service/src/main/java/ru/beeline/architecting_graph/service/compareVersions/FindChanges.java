/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.compareVersions;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.beeline.architecting_graph.model.Pair;
import ru.beeline.architecting_graph.repository.neo4j.*;

import java.util.HashSet;
import java.util.Set;

@Component
public class FindChanges {

    @Autowired
    private RelationshipRepository relationshipRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private DeploymentNodesRepository deploymentNodesRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private DeployRepository deployRepository;

    public enum ChangeType {
        EARLIER, LATER
    }

    public String descriptionRelation(Value leftNode, Value rightNode, String relType, String description) {
        String type = leftNode.asNode().labels().toString();
        type = type.substring(1, type.length() - 1);
        String key = "name";
        if (type.equals("SoftwareSystem")) {
            key = "cmdb";
        }
        String val = leftNode.asNode().asMap().get(key).toString();
        String res = "\t\t{\n" + "\t\t\t\"type\": \"Relation\",\n";
        res = res + "\t\t\t\"relation_type\": \"" + relType + "\",\n";
        res = res + "\t\t\t\"description\": \"" + description + "\",\n";
        res = res + "\t\t\t\"from\": {\n\t\t\t\t\"" + key + "\": \"" + val + "\",\n";
        res = res + "\t\t\t\t\"type\": \"" + type + "\",\n\t\t\t},\n";
        type = rightNode.asNode().labels().toString();
        type = type.substring(1, type.length() - 1);
        key = "name";
        if (type.equals("SoftwareSystem")) {
            key = "cmdb";
        }
        val = rightNode.asNode().asMap().get(key).toString();
        res = res + "\t\t\t\"to\": {\n\t\t\t\t\"" + key + "\": \"" + val + "\",\n";
        res = res + "\t\t\t\t\"type\": \"" + type + "\",\n\t\t\t}\n\t\t}";
        return res;
    }

    private void processRelationships(String label, String name, int v1, int v2, int curVersion,
                                      String cmdb, boolean flag, Set<Pair> out) {
        Result result = relationshipRepository.getRelationships(label, name, cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            int start = parseVersion(record.get("r.startVersion"), 0);
            int end = parseVersion(record.get("r.endVersion"), curVersion);
            if (isVersionInRange(start, end, v1, v2, flag)) {
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
    }

    private int parseVersion(Value value, int fallback) {
        String str = value.toString();
        if (str.equals("NULL")) return fallback;
        str = str.substring(1, str.length() - 1);
        return Integer.parseInt(str);
    }

    private boolean isVersionInRange(int start, int end, int v1, int v2, boolean flag) {
        return flag ? (start <= v1 && v1 <= end && end < v2)
                : (v1 < start && start <= v2 && v2 <= end);
    }

    private void processChildren(String parentType, String childType, String parentName,
                                 int v1, int v2, int curVersion, String cmdb, boolean flag, Set<Pair> out) {
        Result result = relationshipRepository.getChildren(parentType, childType, parentName);
        while (result.hasNext()) {
            Record record = result.next();
            int start = parseVersion(record.get("m.startVersion"), 0);
            int end = parseVersion(record.get("m.endVersion"), curVersion);
            String name = record.get("m.name").asString();
            if (isVersionInRange(start, end, v1, v2, flag)) {
                out.add(new Pair(name, childType));
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Child", "Child"), "Relationship"));
            }
            if (childType.equals("DeploymentNode")) {
                checkDeploymentNodes(name, v1, v2, curVersion, cmdb, flag, out);
            }
            if (!childType.equals("DeploymentNode")) {
                processRelationships(childType, name, v1, v2, curVersion, cmdb, flag, out);
            }
        }
    }

    private void checkDeploymentNodes(String name, Integer v1, Integer v2,
                                      Integer curVersion, String cmdb, Boolean flag, Set<Pair> out) {
        processRelationships("DeploymentNode", name, v1, v2, curVersion, cmdb, flag, out);
        processChildren("DeploymentNode", "DeploymentNode", name, v1, v2, curVersion, cmdb, flag, out);
        processChildren("DeploymentNode", "InfrastructureNode", name, v1, v2, curVersion, cmdb, flag, out);
        processChildren("DeploymentNode", "ContainerInstance", name, v1, v2, curVersion, cmdb, flag, out);
    }

    private boolean isChangeMatch(ChangeType type, int start, int end, int v1, int v2) {
        return switch (type) {
            case EARLIER -> start <= v1 && v1 <= end && end < v2;
            case LATER -> v1 < start && start <= v2 && v2 <= end;
        };
    }

    public Set<Pair> earlierChanges(Integer v1, Integer v2, Integer curVersion, String cmdb) {
        Set<Pair> out = new HashSet<>();
        processSystemRelationships(cmdb, v1, v2, curVersion, ChangeType.EARLIER, out);
        processContainers(cmdb, v1, v2, curVersion, ChangeType.EARLIER, out);
        processSoftwareSystemInstances(cmdb, v1, v2, curVersion, ChangeType.EARLIER, out);
        processDeploymentNodes(cmdb, v1, v2, curVersion, ChangeType.EARLIER, out);
        return out;
    }

    public Set<Pair> laterChanges(Integer v1, Integer v2, Integer curVersion, String cmdb) {
        Set<Pair> out = new HashSet<>();
        processSystemRelationships(cmdb, v1, v2, curVersion, ChangeType.LATER, out);
        processContainers(cmdb, v1, v2, curVersion, ChangeType.LATER, out);
        processSoftwareSystemInstances(cmdb, v1, v2, curVersion, ChangeType.LATER, out);
        processDeploymentNodes(cmdb, v1, v2, curVersion, ChangeType.LATER, out);
        return out;
    }

    private void processSystemRelationships(String cmdb, Integer v1, Integer v2, Integer curVersion, ChangeType type,
                                            Set<Pair> out) {
        Result result = relationshipRepository.getSystemRelationshipsOut(cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("r.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (isChangeMatch(type, start, end, v1, v2)) {
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
        result = relationshipRepository.getSystemRelationshipsIn(cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("r.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (isChangeMatch(type, start, end, v1, v2)) {
                out.add(new Pair(descriptionRelation(record.get("m"), record.get("n"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
    }

    private void processContainerRelationships(String containerName, Integer v1, Integer v2, Integer curVersion, String cmdb,
                                               Set<Pair> out) {
        Result containerResult = relationshipRepository.getContainerRelationshipsOut(containerName, cmdb);
        while (containerResult.hasNext()) {
            Record record = containerResult.next();
            Integer startVersionContainerRel = getVersion(record.get("r.startVersion").toString(), curVersion);
            Integer endVersionContainerRel = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (startVersionContainerRel <= v1 && v1 <= endVersionContainerRel && endVersionContainerRel < v2) {
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
        Result containerResultRev = relationshipRepository.getContainerRelationshipsIn(containerName, cmdb);
        while (containerResultRev.hasNext()) {
            Record record = containerResultRev.next();
            Integer startVersionContainerRelRev = getVersion(record.get("r.startVersion").toString(), curVersion);
            Integer endVersionContainerRelRev = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (startVersionContainerRelRev <= v1 && v1 <= endVersionContainerRelRev && endVersionContainerRelRev < v2) {
                out.add(new Pair(descriptionRelation(record.get("m"), record.get("n"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
    }

    private void processComponents(String containerName, Integer v1, Integer v2, Integer curVersion, String cmdb,
                                   Set<Pair> out) {
        Result componentResult = componentRepository.getContainerComponents(containerName);
        while (componentResult.hasNext()) {
            Record record = componentResult.next();
            Integer startVersionComponent = getVersion(record.get("m.startVersion").toString(), curVersion);
            Integer endVersionComponent = getVersion(record.get("m.endVersion").toString(), curVersion);
            String componentName = record.get("m.name").asString();
            if (startVersionComponent <= v1 && v1 <= endVersionComponent && endVersionComponent < v2) {
                out.add(new Pair(componentName, "Component"));
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Child", "Child"), "Relationship"));
            }
            Result componentRelResult = relationshipRepository.getComponentRelationshipsOut(componentName, cmdb);
            while (componentRelResult.hasNext()) {
                record = componentRelResult.next();
                Integer startVersionComponentRel = getVersion(record.get("r.startVersion").toString(), curVersion);
                Integer endVersionComponentRel = getVersion(record.get("r.endVersion").toString(), curVersion);
                if (startVersionComponentRel <= v1 && v1 <= endVersionComponentRel && endVersionComponentRel < v2) {
                    out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Relationship",
                            record.get("r.description").asString()), "Relationship"));
                }
            }
            Result componentRelResultRev = relationshipRepository.getComponentRelationshipsIn(componentName, cmdb);
            while (componentRelResultRev.hasNext()) {
                record = componentRelResultRev.next();
                Integer startVersionComponentRelRev = getVersion(record.get("r.startVersion").toString(), curVersion);
                Integer endVersionComponentRelRev = getVersion(record.get("r.endVersion").toString(), curVersion);
                if (startVersionComponentRelRev <= v1 && v1 <= endVersionComponentRelRev && endVersionComponentRelRev < v2) {
                    out.add(new Pair(descriptionRelation(record.get("m"), record.get("n"), "Relationship",
                            record.get("r.description").asString()), "Relationship"));
                }
            }
        }
    }

    private Integer getVersion(String versionString, Integer curVersion) {
        if (versionString.equals("NULL")) {
            return curVersion;
        }
        versionString = versionString.substring(1, versionString.length() - 1);
        return Integer.parseInt(versionString);
    }

    private void processContainers(String cmdb, Integer v1, Integer v2, Integer curVersion, ChangeType type, Set<Pair> out) {
        Result result = containerRepository.getContainersByCmdb(cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("m.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("m.endVersion").toString(), curVersion);
            String containerName = record.get("m.name").asString();
            if (isChangeMatch(type, start, end, v1, v2)) {
                out.add(new Pair(containerName, "Container"));
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Child", "Child"), "Relationship"));
            }
            if (type == ChangeType.EARLIER) {
                processContainerRelationships(containerName, v1, v2, curVersion, cmdb, out);
                processComponents(containerName, v1, v2, curVersion, cmdb, out);
            } else {
                processNodeRelationships("Container", containerName, cmdb, v1, v2, curVersion, out);
                processComponents(containerName, cmdb, v1, v2, curVersion, out);
            }
        }
    }

    private void processComponents(String containerName, String cmdb, Integer v1, Integer v2, Integer curVersion, Set<Pair> out) {
        Result result = componentRepository.getComponentsByContainerName(containerName);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("m.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("m.endVersion").toString(), curVersion);
            String componentName = record.get("m.name").asString();
            if (v1 < start && start <= v2 && v2 <= end) {
                out.add(new Pair(componentName, "Component"));
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Child", "Child"), "Relationship"));
            }
            processNodeRelationships("Component", componentName, cmdb, v1, v2, curVersion, out);
        }
    }

    private void processNodeRelationships(String label, String name, String cmdb, Integer v1, Integer v2, Integer curVersion,
                                          Set<Pair> out) {
        Result result = relationshipRepository.getNodeRelationshipsOut(label, name, cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("r.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (v1 < start && start <= v2 && v2 <= end) {
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
        result = relationshipRepository.getNodeRelationshipsIn(label, name, cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("r.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (v1 < start && start <= v2 && v2 <= end) {
                out.add(new Pair(descriptionRelation(record.get("m"), record.get("n"), "Relationship",
                        record.get("r.description").asString()), "Relationship"));
            }
        }
    }

    private void processSoftwareSystemInstances(String cmdb, Integer v1, Integer v2,
                                                Integer curVersion, ChangeType type, Set<Pair> out) {
        Result result = deployRepository.getSoftwareSystemInstances(cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            int start = getVersion(record.get("r.startVersion").toString(), curVersion);
            int end = getVersion(record.get("r.endVersion").toString(), curVersion);
            if (isChangeMatch(type, start, end, v1, v2)) {
                out.add(new Pair(
                        descriptionRelation(record.get("n"), record.get("m"), "SoftwareSystemInstance", "Deploy"),
                        "SoftwareSystemInstance"
                ));
            }
        }
    }

    private void processDeploymentNodes(String cmdb, Integer v1, Integer v2, Integer curVersion, ChangeType changeType,
                                        Set<Pair> out) {
        Result result = deploymentNodesRepository.getDeploymentNodesByCmdb(cmdb);
        while (result.hasNext()) {
            Record record = result.next();
            Integer start = getVersion(record.get("m.startVersion").toString(), curVersion);
            Integer end = getVersion(record.get("m.endVersion").toString(), curVersion);
            String name = record.get("m.name").asString();
            if (isChangeMatch(changeType, start, end, v1, v2)) {
                out.add(new Pair(name, "DeploymentNode"));
                out.add(new Pair(descriptionRelation(record.get("n"), record.get("m"), "Child", "Child"), "Relationship"));
            }
            checkDeploymentNodes(name, v1, v2, curVersion, cmdb, changeType == ChangeType.EARLIER, out);
        }
    }
}
