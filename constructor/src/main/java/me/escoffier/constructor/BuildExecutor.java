package me.escoffier.constructor;


import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@ApplicationScoped
public class BuildExecutor {

    final static AtomicInteger id = new AtomicInteger();

    @Inject
    Process process;

    @Inject
    Yaml yaml;

    private Build build;
    private File local;
    private File work;

    private static final Logger LOGGER = Logger.getLogger("StepExecutor");
    private Map<String, String> versions;
    private Map<String, String> variables;
    private File buildFileDirectory;
    private Report report;
    private String resumeFrom;
    private boolean resumed;

    public void init(File file, Build build, File buildFileDirectory, File localRepository, File workDirectory, Map<String, String> variables, String resumeFrom) {
        this.build = build;
        this.resumeFrom = resumeFrom == null || resumeFrom.isBlank() ? null : resumeFrom;
        this.report = new Report(file, build);
        this.local = localRepository;
        this.work = workDirectory;
        this.buildFileDirectory = buildFileDirectory;

        this.variables = new HashMap<>();
        if (variables != null) {
            this.variables.putAll(variables);
        }
        for (Map.Entry<String, String> entry : build.variables.entrySet()) {
            this.variables.putIfAbsent(entry.getValue(), entry.getKey());
        }

        this.versions = new HashMap<>();
        if (build.versions != null) {
            this.versions.putAll(build.versions);
        }
        for (Map.Entry<String, String> entry : new HashSet<>(this.versions.entrySet())) {
            this.versions.put(entry.getKey(), Variables.expand(this.variables, entry.getValue()));
        }

        importPipelines(build);

        int numberOfPipelines = this.build.pipelines.size();
        long numberOfSteps = this.build.pipelines.stream().map(p -> p.steps.size()).mapToInt(i -> i).sum();
        LOGGER.infof("Initializing build: %d pipelines, %d steps", numberOfPipelines, numberOfSteps);

        if (resumeFrom != null) {
            this.build.pipelines.stream().flatMap(p -> p.steps.stream())
                    .filter(s -> s.repository.equalsIgnoreCase(resumeFrom))
                    .findAny().orElseThrow(() -> new NoSuchElementException("Unable to resume from step " + resumeFrom + " : step not found"));
        }
        resumed = this.resumeFrom == null;

        for (Pipeline pipeline : this.build.pipelines) {
            pipeline.steps.forEach(s -> {
                if (s.version != null) {
                    this.versions.put(s.repository, s.version);
                }
            });
        }
    }

    private void importPipelines(Build build) {
        for (Pipeline pipeline : build.pipelines) {
            if (pipeline.file != null) {
                File file = new File(buildFileDirectory, pipeline.file);
                Pipeline p = yaml.readPipeline(file);
                pipeline.steps = p.steps;
                pipeline.skipTests = p.skipTests;
            }
        }
    }

    public boolean executePipeline(Pipeline pipeline) {
        report.beginPipeline(pipeline);
        for (Step step : pipeline.steps) {
            report.beginStep(pipeline, step);
            LOGGER.infof("Building %s from pipeline %s", step.repository, pipeline.name);
            try {
                buildStep(id.getAndIncrement(), pipeline, step);
                report.completedStep(pipeline, step);
            } catch (Exception e) {
                report.failedStep(pipeline, step, e);
                report.failedPipeline(pipeline);
                return false;
            }
        }
        report.completedPipeline(pipeline);
        return true;
    }

    public void buildStep(int id, Pipeline pipeline, Step step) {
        String repo = Variables.expand(variables, step.repository);
        String branch = Variables.expand(variables, step.branchOrCommit);
        var out = new File(work, toFileName(repo));

        if (! resumed  && step.repository.equalsIgnoreCase(resumeFrom)) {
            resumed = true;
        }

        if (resumed) {
            LOGGER.infof("Executing step for %s", repo);
            delete(out);
            // Step 1 - Clone
            LOGGER.infof("Cloning project %s in %s and switching to branch/commit %s", repo, out.getAbsolutePath(), branch);
            clone(id, out, repo, branch);

            // Step 2 - Update references
            LOGGER.infof("Updating project dependencies");
            updateProject(id, repo, out, step.dependencies);

            // Step 3 - Execute commands
            step.commands.forEach(command -> executeBuildCommand(id, repo, pipeline, out, command));
        }

        // Step 4 - Get version if not set - even if resuming, so we collect versions
        if (step.version == null) {
            String version = extractVersion(id, repo, step, out);
            LOGGER.infof("Extracted version for %s: %s", repo, version);
            versions.put(step.repository, version);
        } else {
            versions.put(step.repository, step.version);
        }
    }

    private void executeBuildCommand(int id, String repo, Pipeline pipeline, File out, String command) {
        String expanded = Variables.expand(variables, command);
        LOGGER.infof("Executing build command for %s : %s", repo, expanded);
        if (!expanded.contains("-DskipTests") && pipeline.skipTests) {
            expanded += " -DskipTests -DskipITs";
        }
        expanded += " -Dmaven.repo.local=" + local.getAbsolutePath();
        File settings = new File("maven-settings.xml");
        if (settings.isFile()) {
            expanded += " -s " + settings.getAbsolutePath();
        }
        String ti = getTaskId(id, 3);
        try {
            String commandToBeExecuted = expanded;
            executeOrFailed(repo, expanded, () -> process.splitAndExecute(ti + "-build", out, commandToBeExecuted));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateProject(int id, String repo, File out, Map<String, String> dependencies) {
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String resolved = versions.get(entry.getValue());
            if (resolved == null) {
                LOGGER.errorf("Cannot resolve reference to %s (%s). The project is not declared in the pipeline. Only %s are declared", entry.getKey(), entry.getValue(), versions.keySet());
                throw new RuntimeException("Unable to resolve reference to " + entry.getValue());
            }

            String ti = getTaskId(id, 2);
            if (entry.getKey().contains(":")) {
                executeOrFailed(repo, "mvn:use-dep-version", () -> {
                    LOGGER.infof("Setting version for dependency %s to %s", entry.getKey(), resolved);
                    process.execute(ti + "-update-project", out, "mvn", "versions:use-dep-version", "-Dincludes=" + entry.getKey(), "-DdepVersion=" + resolved, "-DforceVersion=true", "-Dmaven.repo.local=" + local.getAbsolutePath());
                });
            } else {
                executeOrFailed(repo, "mvn:set-property", () -> {
                    LOGGER.infof("Setting version into variable %s to %s", entry.getKey(), resolved);
                    process.execute(ti + "-update-project", out, "mvn", "versions:set-property", "-Dproperty=" + entry.getKey(), "-DnewVersion=" + resolved, "-Dmaven.repo.local=" + local.getAbsolutePath());
                });
            }
        }
    }

    private String extractVersion(int id, String repo, Step step, File out) {
        String ti = getTaskId(id, 4);
        return executeOrFailed(repo, "Maven version extraction", () ->
                process.executeAndReturn(ti + "-extract-version", out, "mvn", "-q", "-Dexec.executable=echo", "-Dexec.args='${project.version}'", "-N", "org.codehaus.mojo:exec-maven-plugin:3.0.0:exec", "-Dmaven.repo.local=" + local.getAbsolutePath())
        );
    }

    private void clone(int id, File out, String repo, String branchOrCommit) {
        delete(out);
        var url = "https://github.com/" + repo;
        String ti = getTaskId(id, 0);
        executeOrFailed(repo, "git clone " + url + " " + out.getName(), () -> {
            process.execute(ti + "-clone-" + toFileName(repo), work, "git", "clone", url, out.getName());
            if (branchOrCommit != null) {
                process.execute(ti + "-checkout", out, "git", "checkout", branchOrCommit);
            }
        });
    }

    private void executeOrFailed(String repo, String command, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            throw  new ConstructorException(repo, command, e);
        }
    }

    private <T> T executeOrFailed(String repo, String command, Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            throw  new ConstructorException(repo, command, e);
        }
    }

    private String getTaskId(int id, int task) {
        return String.format("%04d", id * 100 + task);
    }

    private String toFileName(String repo) {
        return repo.replace("/", "_");
    }

    private void delete(File file) {
        File[] allContents = file.listFiles();
        if (allContents != null) {
            for (File f : allContents) {
                delete(f);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public boolean go() {
        boolean completed = true;
        report.begin();

        for (Pipeline pipeline : build.pipelines) {
            LOGGER.infof("Executing pipeline %s", pipeline.name);
            completed = completed  && executePipeline(pipeline);
        }
        report.end();
        return completed;
    }

    public Report getReport() {
        return report;
    }
}
