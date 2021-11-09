package me.escoffier.constructor.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Report {
    private final Build buid;
    private final File file;

    private long start;
    private long end;

    private Exception failure;

    public Report(File file, Build build) {
        this.file = file;
        this.buid = build;
    }

    public void begin() {
        this.start = System.currentTimeMillis();
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
        this.failure = e;
    }

    public void failedPipeline(Pipeline pipeline) {
        events.add("Unable to complete the construction of " + pipeline.name + ", a project was not built correctly");
    }

    public void completedPipeline(Pipeline pipeline) {
        events.add("Pipeline " + pipeline.name + " has been built successfully");
    }

    public void write(File root) {
        StringBuilder report = new StringBuilder("<html><head>" +
                "<title>Build Report</title><link rel=\"stylesheet\" href=\"https://unpkg.com/@picocss/pico@latest/css/pico.min.css\">\n" +
                "<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "</head><body><main class=\"container\">");
        report.append("<h1>Constructor Report</h1>\n");

        report.append("<p><strong>constructor file:</strong> ").append(file.getAbsolutePath()).append("</p>\n");
        report.append("<p><strong>duration:</strong> ").append(getDuration()).append("</p>\n");
        report.append("<p><strong>status:</strong> ").append(getStatus()).append("</p>\n");

        String details = getFailureDetails();
        if (details != null) {
            report.append("<p><strong>details: </strong>").append("<blockquote>").append(details).append("</blockquote></p>\n");
        }

        report.append("<hr/>\n");

        report.append("<ul>\n");
        for (String ev : events) {
            report.append("<li>").append(ev).append("</li>\n");
        }
        report.append("</ul>\n");

        report.append("\n</main></body></html>");

        File out = new File(root,"report.html");
        try {
            Files.write(out.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getStatus() {
        if (failure != null) {
            return "<span class=\"build-failed\">failed</span>";
        } else {
            return "<span class=\"build-success\">success</span>";
        }
    }

    public String getFailureDetails() {
        if (failure == null) {
            return null;
        } else {
            if (failure instanceof ConstructorException) {
                return failure.getMessage();
            }
            return failure.getMessage();
        }
    }

    private String getDuration() {
        long ms = end - start;
        Duration duration = Duration.ofMillis(ms);
        long HH = duration.toHours();
        long MM = duration.toMinutesPart();
        long SS = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", HH, MM, SS);
    }

    public void end() {
        end = System.currentTimeMillis();
    }
}
