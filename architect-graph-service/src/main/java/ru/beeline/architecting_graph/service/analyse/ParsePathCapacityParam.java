package ru.beeline.architecting_graph.service.analyse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ru.beeline.architecting_graph.repository.neo4j.AnalyseRepository;

@Component
public class ParsePathCapacityParam {

    @Autowired
    AnalyseRepository analyseRepository;

    public Boolean empty(String s) {
        if (s == null || s.isEmpty() || s.isBlank()) {
            return true;
        }
        return false;
    }

    public String parseNodeTypes(String nodeTypes) {
        if (empty(nodeTypes)) {
            return "";
        }

        String res = "WHERE ALL(n IN nodes(pathSegment) WHERE ";

        String[] types = nodeTypes.split(",");
        List<String> listTypes = Arrays.stream(types).map(String::trim).collect(Collectors.toList());
        for (String type : listTypes) {
            res = res + "n:" + type + " OR ";
        }

        res = res.substring(0, res.length() - 4);
        res = res + ") ";
        return res;
    }

    public String parseRelTypes(String relTypes) {
        if (empty(relTypes)) {
            return "";
        }

        String res = ":";

        String[] types = relTypes.split(",");
        List<String> listTypes = Arrays.stream(types).map(String::trim).collect(Collectors.toList());
        for (String type : listTypes) {
            res = res + type + "|";
        }

        res = res.substring(0, res.length() - 1);
        return res;
    }

    public String parseNodeIdentifiers(String nodeIdentifiers, String nodeTypes, String relTypes) {
        if (empty(nodeIdentifiers)) {
            return "";
        }

        String[] identifiers = nodeIdentifiers.split(",");
        List<String> listIdentifiers = Arrays.stream(identifiers).map(String::trim).collect(Collectors.toList());

        String  res = "[";
        for (String identifier : listIdentifiers) {
            res = res + "\"" + identifier + "\", ";
        }
        res = res.substring(0, res.length() - 2);
        res = res + "] ";
        Result result = analyseRepository.getPathSize(nodeTypes, relTypes, res);

        if (result.hasNext()) {
            return res;
        }

        return "";
    }
}
