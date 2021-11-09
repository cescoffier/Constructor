package me.escoffier.constructor.helpers;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.escoffier.constructor.build.Build;
import me.escoffier.constructor.build.Pipeline;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;

@ApplicationScoped
public class Yaml {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public ObjectMapper mapper() {
        return mapper;
    }

    public Build readBuild(File file) {
        if (! file.isFile()) {
            throw new RuntimeException("The file " + file.getAbsolutePath() + " does not exist");
        }
        try {
            return mapper().readerFor(Build.class).readValue(file);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the build file", e);
        }
    }

    public Pipeline readPipeline(File file) {
        if (! file.isFile()) {
            throw new RuntimeException("The file " + file.getAbsolutePath() + " does not exist");
        }
        try {
            return mapper().readerFor(Pipeline.class).readValue(file);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the pipeline file", e);
        }
    }

}
