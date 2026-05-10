package ru.beeline.architecting_graph.service.analyse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ru.beeline.architecting_graph.repository.neo4j.AnalyseRepository;

@Component
public class ParseSinglePointsParams {

    @Autowired
    AnalyseRepository analyseRepository;

    public Boolean empty(String s) {
        if (s == null || s.isEmpty() || s.isBlank()) {
            return true;
        }
        return false;
    }

    public String parseNodeTypes(String nodeTypes) {
        String res = "[";

        if (empty(nodeTypes)) {

            Result result = analyseRepository.getElementLabels();
            while (result.hasNext()) {
                Record record = result.next();
                res = res + "'" + record.get("label").asString() + "', ";
            }

        } else {

            String[] types = nodeTypes.split(",");
            List<String> listTypes = Arrays.stream(types).map(String::trim).collect(Collectors.toList());
            for (String type : listTypes) {
                res = res + "'" + type + "', ";
            }

        }

        res = res.substring(0, res.length() - 2);
        res = res + "], ";
        return res;
    }

    public String parseRelTypes(String relTypes) {
        String res = "";

        if (empty(relTypes)) {

            Result result = analyseRepository.getRelationLabels();
            while (result.hasNext()) {
                Record record = result.next();
                res = res + record.get("relationshipType").asString() + ": { orientation: 'UNDIRECTED' }, ";
            }

        } else {

            String[] types = relTypes.split(",");
            List<String> listTypes = Arrays.stream(types).map(String::trim).collect(Collectors.toList());
            for (String type : listTypes) {
                res = res + type + ": { orientation: 'UNDIRECTED' }, ";
            }

        }

        res = res.substring(0, res.length() - 2);
        return res;
    }

    public String parseGraphTag(String graphTag) {
        if (empty(graphTag)) {
            return "";
        }
        return "WHERE node.graphTag = \"" + graphTag + "\" ";
    }

    public String parseNodeIdentifiers(String nodeIdentifiers, String graphTag) {
        if (empty(nodeIdentifiers)) {
            return "";
        }

        String param = "structurizr_dsl_identifier";
        String res = "";

        if (empty(graphTag)) {
            res = "WHERE ";
        } else {
            res = "AND ";
        }

        res = res + "node." + param + " IN [";

        String[] identifiers = nodeIdentifiers.split(",");
        List<String> listIdentifiers = Arrays.stream(identifiers).map(String::trim).collect(Collectors.toList());
        for (String identifier : listIdentifiers) {
            res = res + "\"" + identifier + "\", ";
        }
        res = res.substring(0, res.length() - 2);

        res = res + "] ";
        return res;
    }
}
