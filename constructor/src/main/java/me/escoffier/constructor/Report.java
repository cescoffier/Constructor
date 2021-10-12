package me.escoffier.constructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Report {
    private final Build buid;

    public Report(Build build) {
        this.buid = build;
    }


    public void beginPipeline(Pipeline pipeline) {
        events.add("Beginning execution of pipeline " + pipeline.name);
    }

    public void beginStep(Pipeline pipeline, Step step) {
        events.add("Beginning building " + step.repository + " (from pipeline " + pipeline.name + ")");
    }

    private final List<String> events = new ArrayList<>();


    public void completedStep(Pipeline pipeline, Step step) {
        events.add("Completed the build of " + step.repository + " successfully");
    }

    public void failedStep(Pipeline pipeline, Step step, Exception e) {
        events.add("The build of " + step.repository + " failed with " + e.getMessage());
    }

    public void failedPipeline(Pipeline pipeline) {
        events.add("Unable to complete the construction of " + pipeline.name + ", a project was not built correctly");
    }

    public void completedPipeline(Pipeline pipeline) {
        events.add("Pipeline " + pipeline.name + " has been built successfully");
    }

    public void write(File root) {
        StringBuilder report = new StringBuilder("<html><head><title>Build Report</title></head><body>'n");
        report.append("<h1>Report</h1>\n");
        report.append("<ul>\n");
        for (String ev : events) {
            report.append("<li>").append(ev).append("</li>\n");
        }
        report.append("</ul>\n");

        report.append("\n</body></html>");

        File out = new File(root,"report.html");
        try {
            Files.write(out.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
