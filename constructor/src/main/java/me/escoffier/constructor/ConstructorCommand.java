package me.escoffier.constructor;

import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Command(name = "constructor", mixinStandardHelpOptions = true)
public class ConstructorCommand implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger("Constructor");

    @Parameters(paramLabel = "<build-file>", defaultValue = "constructor.yaml",
        description = "The constructor build description")
    File file;

    @CommandLine.Option(names = "--clean", description = "Remove the working directory and the local respository before building")
    boolean clean;

    @CommandLine.Option(names = {"--work-dir", "-w"}, description = "The working directory", defaultValue = "construction-work")
    File work;

    @CommandLine.Option(names = {"--local-repository", "-r"}, description = "The local repository directory", defaultValue = "repo")
    File repo;

    @CommandLine.Option(names = {"--variables"}, description = "Define a new variable")
    Map<String, String> variables;

    @Inject Yaml yaml;
    @Inject
    BuildExecutor executor;

    @Override
    public Integer call() throws IOException {
        LOGGER.infof("Reading file %s", file.getAbsolutePath());
        Build build;
        try {
            build = yaml.readBuild(file);
        } catch (Exception e) {
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

        executor.init(file, build, file.getParentFile(), repo, work, variables);
        boolean completed = executor.go();

        executor.getReport().write(file.getParentFile());

        zip();

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
