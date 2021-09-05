package me.escoffier.constructor;

import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@Command(name = "constructor", mixinStandardHelpOptions = true)
public class ConstructorCommand implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger("Constructor");

    @Parameters(paramLabel = "<constructor-file>", defaultValue = "constructor.yaml",
        description = "The constructor pipeline description")
    File file;

    @CommandLine.Option(names = {"--work-dir", "-w"}, description = "The working directory", defaultValue = "construction-work")
    File work;

    @CommandLine.Option(names = {"--local-repository", "-r"}, description = "The local repository directory", defaultValue = "repo")
    File repo;

    @Inject Yaml yaml;
    @Inject StepExecutor executor;

    @Override
    public Integer call() {
        LOGGER.infof("Reading descriptor %s", file.getAbsolutePath());
        // Read descriptor
        Pipeline pipeline = yaml.read(file);

        repo.mkdirs();
        work.mkdirs();

        executor.init(pipeline, repo, work);
        LOGGER.info("Executing...");
        AtomicInteger task = new AtomicInteger();
        for (Step step : pipeline.steps) {
            try {
                executor.execute(task.incrementAndGet(), step);
            } catch (RuntimeException e) {
                LOGGER.error("Construction failed", e);
                throw e;
            }
        }

        LOGGER.info("Construction completed successfully");
        return 0;
    }



}
