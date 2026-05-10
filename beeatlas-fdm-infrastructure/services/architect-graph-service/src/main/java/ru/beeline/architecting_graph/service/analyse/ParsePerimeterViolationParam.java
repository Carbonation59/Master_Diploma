package ru.beeline.architecting_graph.service.analyse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ParsePerimeterViolationParam {
    public Boolean empty(String s) {
        if (s == null || s.isEmpty() || s.isBlank()) {
            return true;
        }
        return false;
    }

    public String parseDeploymentNodeIdentifier(String deploymentNodeIdentifier) {
        if (empty(deploymentNodeIdentifier)) {
            return "\"\" ";
        }

        return "\"" + deploymentNodeIdentifier + "\" ";
    }

    public String parseNodeProperties(String nodeProperties) {
        if (empty(nodeProperties)) {
            return "";
        }

        String res = "OR (";

        String[] properties = nodeProperties.split(",");
        List<String> listProperties = Arrays.stream(properties).map(String::trim).collect(Collectors.toList());
        for (String property : listProperties) {
            res = res + "elem.technology <> \"" + property + "\" AND ";
        }
        res = res.substring(0, res.length() - 5);

        res = res + ") ";
        return res;
    }
}
