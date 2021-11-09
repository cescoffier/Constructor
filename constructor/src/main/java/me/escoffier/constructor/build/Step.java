package me.escoffier.constructor.build;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Step {

    public String repository;
    public String version;
    public String branchOrCommit;
    public List<String> commands = List.of("mvn clean install -DskipTests=true -DskipITs");
    public Map<String, String> dependencies = Collections.emptyMap();




}
