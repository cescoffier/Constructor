package me.escoffier.constructor;

import me.escoffier.constructor.build.Build;
import me.escoffier.constructor.build.BuildExecutor;
import me.escoffier.constructor.build.Pipeline;
import me.escoffier.constructor.helpers.Yaml;
import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;
import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Command(name = "constructor", mixinStandardHelpOptions = true)
public class BuildCommand implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger("Constructor");

    @Parameters(paramLabel = "<build-file>", defaultValue = "constructor.yaml",
        description = "The constructor build description")
    File file;

    @CommandLine.Option(names = "--clean", description = "Remove the working directory and the local repository before building")
    boolean clean;

    @CommandLine.Option(names = "--skip-report", description = "Do not create the HTML report and zip file when the construction completes")
    boolean skipReport;

    @CommandLine.Option(names = {"--work-dir", "-w"}, description = "The working directory", defaultValue = "construction-work")
    File work;

    @CommandLine.Option(names = {"--local-repository", "-r"}, description = "The local repository directory", defaultValue = "repo")
    File repo;

    @CommandLine.Option(names = {"--resume-from", "-rf"}, description = "Resume the build from a specific step. The step is specified using the repository name")
    String resumeFrom;

    @CommandLine.Option(names = {"--variables"}, description = "Define a new variable")
    Map<String, String> variables;

    @Inject
    Yaml yaml;
    @Inject
    BuildExecutor executor;

    @Override
    public Integer call() throws IOException {
        LOGGER.infof("Reading file %s", file.getAbsolutePath());
        Build build;
        try {
            build = yaml.readBuild(file);
        } catch (Exception e) {
            e.printStackTrace();
            // Try as pipeline
            Pipeline pipeline = yaml.readPipeline(file);
            build = new Build();
            build.pipelines = Collections.singletonList(pipeline);
        }

        if (clean) {
            LOGGER.infof("Deleting %s and %s", repo.getAbsolutePath(), work.getAbsolutePath());
            FileUtils.deleteQuietly(repo);
            FileUtils.deleteQuietly(work);
            FileUtils.deleteQuietly(new File(file.getParentFile(), "constructor.zip"));
        }

        //noinspection ResultOfMethodCallIgnored
        repo.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        work.mkdirs();

        executor.init(file, build, file.getParentFile(), repo, work, variables, resumeFrom);
        boolean completed = executor.go();

        if (! skipReport) {
            zip();
            executor.getReport().write(file.getParentFile());
        }

        if (completed) {
            LOGGER.info("Construction completed successfully");
            return 0;
        } else {
            throw new RuntimeException("Construction failed");
        }

    }

    private void zip() throws IOException {
        File out = new File(file.getParentFile(), "constructor.zip");
        FileOutputStream fos = new FileOutputStream(out);
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        zipFile(work, work.getName(), zipOut);
        zipOut.close();
        fos.close();
        LOGGER.info("Zip file created " + out.getAbsolutePath());
    }

    private static void zipFile(File file, String fileName, ZipOutputStream zipOut) throws IOException {
        if (file.isHidden()) {
            return;
        }
        if (file.isDirectory()) {
            if (fileName.contains("target")) {
                return;
            }

            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = file.listFiles();
            assert children != null;
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(file);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }


}
