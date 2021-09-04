package me.escoffier.constructor;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;

@ApplicationScoped
public class Yaml {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());;

    public ObjectMapper mapper() {
        return mapper;
    }

    public Pipeline read(File file) {
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
