package ru.beeline.architecting_graph.service.analyse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ParseCycleParams {

    public Boolean empty(String s){
        if(s == null || s.isEmpty() || s.isBlank()) {
            return true;
        }
        return false;
    }

    public String parseNodeIdentifiers(String nodeIdentifiers){
        if(empty(nodeIdentifiers)) {
            return "";
        }

        String param = "structurizr_dsl_identifier";
        String res = "WHERE n." + param + " IN [";

        String[] identifiers = nodeIdentifiers.split(",");
        List<String> listIdentifiers = Arrays.stream(identifiers).map(String::trim).collect(Collectors.toList());
        for (String identifier : listIdentifiers) {
            res = res + "\"" + identifier + "\", ";
        }
        res = res.substring(0, res.length() - 2);

        res = res + "] ";
        return res;
    }

    public String parseRelTypes(String relTypes){
        if(empty(relTypes)) {
            return "";
        }
        String res = ", {relTypes: [";

        String[] types = relTypes.split(",");
        List<String> listTypes = Arrays.stream(types).map(String::trim).collect(Collectors.toList());
        for (String type : listTypes) {
            res = res + "\"" + type + "\", ";
        }
        res = res.substring(0, res.length() - 2);

        res = res + "]}";
        return res;
    }

    public String parseNodeTypes(String nodeTypes, String graphTag){
        if(empty(nodeTypes)) {
            return "";
        }
        String res = "WHERE ALL(node IN nodes(path) WHERE labels(node) IN [";

        String[] types = nodeTypes.split(",");
        List<String> listTypes = Arrays.stream(types).map(String::trim).collect(Collectors.toList());
        for (String type : listTypes) {
            res = res + "[\"" + type + "\"], ";
        }
        res = res.substring(0, res.length() - 2);

        res = res + "]";
        if(empty(graphTag)) {
            res = res + ")";
        }
        res = res + " ";
        return res;
    }

    public String parseGraphTag(String graphTag, String nodeTypes){
        if(empty(graphTag)) {
            return "";
        }
        if(empty(nodeTypes)) {
            return "WHERE ALL(node IN nodes(path) WHERE node.graphTag = \"" + graphTag + "\") ";
        }
        return "AND node.graphTag = \"" + graphTag + "\") ";
    }
    
}
