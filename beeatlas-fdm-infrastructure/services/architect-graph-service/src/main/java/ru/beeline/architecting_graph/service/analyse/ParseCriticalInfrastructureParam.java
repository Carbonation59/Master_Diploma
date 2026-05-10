package ru.beeline.architecting_graph.service.analyse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ParseCriticalInfrastructureParam {

    public Boolean empty(String s) {
        if (s == null || s.isEmpty() || s.isBlank()) {
            return true;
        }
        return false;
    }

    public String parseNodeIdentifiers(String nodeIdentifiers) {
        if (empty(nodeIdentifiers)) {
            return "[]";
        }

        String res = "[";

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
