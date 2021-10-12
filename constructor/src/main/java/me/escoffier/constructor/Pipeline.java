package me.escoffier.constructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Pipeline {

    public String name;

    public boolean skipTests;

    public List<Step> steps = Collections.emptyList();

    public String file;

}
