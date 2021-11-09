package me.escoffier.constructor.build;

import java.util.Collections;
import java.util.List;

public class Pipeline {

    public String name;

    public boolean skipTests;

    public List<Step> steps = Collections.emptyList();

    public String file;

}
