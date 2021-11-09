package me.escoffier.constructor.build;

import java.util.Map;

public class Variables {

    private Variables() {
        // Avoid direct instantiation
    }

    public static String expand(Map<String, String> variables, String value) {
        String result = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = value.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

}
