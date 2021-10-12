package me.escoffier.constructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Build {

    public List<Pipeline> pipelines = new ArrayList<>();

    public Map<String, String> versions = Collections.emptyMap();

    public Map<String, String> variables = Collections.emptyMap();

    public final Report report = new Report(this);

    public void dumpReport() {

    }

}
