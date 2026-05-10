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
public class DeployRepository {
    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    public Result getSoftwareSystemInstances(String cmdb) {
        String query = "MATCH (n:SoftwareSystem {graphTag: \"Global\", cmdb: $val1})-[r:Deploy {sourceWorkspace: $cmdb}]->(m) "
                +
                "RETURN n, m, r.startVersion, r.endVersion";
        Value parameters = Values.parameters("val1", cmdb, "cmdb", cmdb);
        return neo4jSessionManager.getSession().run(query, parameters);
    }

}
