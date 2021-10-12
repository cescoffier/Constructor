package me.escoffier.constructor;


import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    public void init(Build build, File buildFileDirectory, File localRepository, File workDirectory) {
        this.build = build;
        this.report = build.report;
        this.local = localRepository;
        this.work = workDirectory;
        this.buildFileDirectory = buildFileDirectory;
        this.versions = new HashMap<>(build.versions);
        this.variables = new HashMap<>(build.variables);

        importPipelines(build);

        int numberOfPipelines = this.build.pipelines.size();
        long numberOfSteps = this.build.pipelines.stream().map(p -> p.steps.size()).mapToInt(i -> i).sum();

        LOGGER.infof("Initializing build: %d pipelines, %d steps", numberOfPipelines, numberOfSteps);

        //this.versions.putAll(this.pipeline.versions); // TODO Local version
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

    public void executePipeline(Pipeline pipeline) {
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
                return;
            }
        }
        report.completedPipeline(pipeline);
    }

    public void buildStep(int id, Pipeline pipeline, Step step) {
        LOGGER.infof("Executing step for %s", step.repository);
        var out = new File(work, toFileName(step.repository));
        delete(out);
        // Step 1 - Clone
        LOGGER.infof("Cloning project %s in %s and switching to branch/commit %s", step.repository, out.getAbsolutePath(), step.branchOrCommit);
        clone(id, out, step.repository, step.branchOrCommit);

        // Step 2 - Update references
        LOGGER.infof("Updating project dependencies");
        updateProject(id, out, step.dependencies);

        // Step 3 - Execute commands
        step.commands.forEach(command -> executeBuildCommand(id, step, pipeline, out, command));

        // Step 4 - Get version if not set
        if (step.version == null) {
            String version = extractVersion(id, step, out);
            LOGGER.infof("Extracted version for %s: %s", step.repository, version);
            versions.put(step.repository, version);
        } else {
            versions.put(step.repository, step.version);
        }
    }



    private void executeBuildCommand(int id, Step step, Pipeline pipeline, File out, String command) {
        LOGGER.infof("Executing build command for %s : %s", step.repository, command);
        if (!command.contains("-DskipTests") && pipeline.skipTests) {
            command += " -DskipTests -DskipITs";
        }
        command += " -Dmaven.repo.local=" + local.getAbsolutePath();
        File settings = new File("maven-settings.xml");
        if (settings.isFile()) {
            command += " -s " + settings.getAbsolutePath();
        }
        String ti = getTaskId(id, 3);
        try {
            process.splitAndExecute(ti + "-build", out, command);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateProject(int id, File out, Map<String, String> dependencies) {
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String resolved = versions.get(entry.getValue());
            if (resolved == null) {
                LOGGER.errorf("Cannot resolve reference to %s (%s). The project is not declared in the pipeline. Only %s are declared", entry.getKey(), entry.getValue(), versions.keySet());
                throw new RuntimeException("Unable to resolve reference to " + entry.getValue());
            }

            String ti = getTaskId(id, 2);
            if (entry.getKey().contains(":")) {
                LOGGER.infof("Setting version for dependency %s to %s", entry.getKey(), resolved);
                process.execute(ti + "-update-project", out, "mvn", "versions:use-dep-version", "-Dincludes=" + entry.getKey(), "-DdepVersion=" + resolved, "-DforceVersion=true", "-Dmaven.repo.local=" + local.getAbsolutePath());
            } else {
                LOGGER.infof("Setting version into variable %s to %s", entry.getKey(), resolved);
                process.execute(ti + "-update-project", out, "mvn", "versions:set-property", "-Dproperty=" + entry.getKey(), "-DnewVersion=" + resolved, "-Dmaven.repo.local=" + local.getAbsolutePath());
            }
        }
    }

    private String extractVersion(int id, Step step, File out) {
        String ti = getTaskId(id, 4);
        return process.executeAndReturn(ti + "-extract-version", out, "mvn", "-q", "-Dexec.executable=echo", "-Dexec.args='${project.version}'", "-N", "org.codehaus.mojo:exec-maven-plugin:3.0.0:exec");
    }

    private void clone(int id, File out, String repo, String branchOrCommit) {
        delete(out);
        var url = "https://github.com/" + repo;
        String ti = getTaskId(id, 0);
        process.execute(ti + "-clone-" + toFileName(repo), work, "git", "clone", url, out.getName());
        if (branchOrCommit != null) {
            process.execute(ti + "-checkout", out, "git", "checkout", branchOrCommit);
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

    public void go() {
        for (Pipeline pipeline : build.pipelines) {
            LOGGER.infof("Executing pipeline %s", pipeline.name);
            executePipeline(pipeline);
        }
    }
}
