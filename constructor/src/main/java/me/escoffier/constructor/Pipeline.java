package me.escoffier.constructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Pipeline {

    public boolean skipTests;

    public List<Step> steps = Collections.emptyList();

    public Map<String, String> versions = Collections.emptyMap();



}
