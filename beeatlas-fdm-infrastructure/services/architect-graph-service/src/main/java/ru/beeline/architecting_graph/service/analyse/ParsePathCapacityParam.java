package ru.beeline.architecting_graph.service.analyse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
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

    public List<List<String>> getPermutations(List<String> list) {
        List<List<String>> result = new ArrayList<>();
        int n = list.size();
        int[] c = new int[n];
        result.add(new ArrayList<>(list));
        int i = 0;
        while (i < n) {
            if (c[i] < i) {
                Collections.swap(list, i % 2 == 0 ? 0 : c[i], i);
                result.add(new ArrayList<>(list));
                c[i]++;
                i = 0;
            } else {
                c[i] = 0;
                i++;
            }
        }
        return result;
    }

    public String parseNodeIdentifiers(String nodeIdentifiers, String nodeTypes, String relTypes) {
        if (empty(nodeIdentifiers)) {
            return "";
        }

        int minPath = (int) 1e9;
        String ans = "";

        String[] identifiers = nodeIdentifiers.split(",");
        List<String> listIdentifiers = Arrays.stream(identifiers).map(String::trim).collect(Collectors.toList());
        List<List<String>> pathPermutations = getPermutations(listIdentifiers);
        for (List<String> path : pathPermutations) {

            String  res = "[";

            for (String identifier : path) {
                res = res + "\"" + identifier + "\", ";
            }

            res = res.substring(0, res.length() - 2);
    
            res = res + "] ";

            Result result = analyseRepository.getPathSize(nodeTypes, relTypes, res);

            if (result.hasNext()) {
                Record record = result.next();
                int pathLength = record.get("pathLength").asInt();
                if(pathLength < minPath) {
                    minPath = pathLength;
                    ans = res;
                }
            }
        }

        return ans;
    }
}
