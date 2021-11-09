package me.escoffier.constructor.build;

import org.jboss.logging.Logger;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;

import javax.enterprise.context.ApplicationScoped;
import java.io.*;

@ApplicationScoped
public class Process {

    private static final Logger LOGGER = Logger.getLogger("Process");

    public void execute(String taskId, File wd, String... commands) {
        LOGGER.infof("Executing task %s : %s", taskId, String.join(" ", commands));
        File log = new File(wd, taskId + ".log");
        try (FileOutputStream fos = new FileOutputStream(log)) {
            int res = initExecutor(taskId, wd, fos)
                    .command(commands)
                    .execute()
                    .getExitValue();

            ensureSuccess(res);

        } catch (Exception e) {
            LOGGER.errorf("Unable to executed `%s`", String.join(" ", commands), e);
            throw new RuntimeException(e);
        }
    }

    public String executeAndReturn(String taskId, File wd, String... commands) {
        LOGGER.infof("Executing task %s : %s", taskId, String.join(" ", commands));
        File log = new File(wd, taskId + ".log");
        try (FileOutputStream fos = new FileOutputStream(log)) {
            return initExecutor(taskId, wd, fos)
                    .command(commands)
                    .execute()
                    .getOutput().getUTF8();
        } catch (Exception e) {
            LOGGER.errorf("Unable to executed `%s`", String.join(" ", commands), e);
            throw new RuntimeException(e);
        }
    }

    private ProcessExecutor initExecutor(String taskId, File wd, FileOutputStream fos) {
        return new ProcessExecutor()
                .directory(wd)
                .readOutput(true)
                .redirectOutput(new LogOutputStream() {
                    @Override
                    protected void processLine(String s) {
                        if (! filteredOut(s)) {
                            LOGGER.infof("[%s] - %s", taskId, s);
                        }
                    }
                })
                .redirectOutputAlsoTo(fos)
                .redirectError(new LogOutputStream() {
                    @Override
                    protected void processLine(String s) {
                        if (! filteredOut(s)) {
                            LOGGER.warnf("[%s] - %s", taskId, s);
                        }
                    }
                })
                .redirectErrorAlsoTo(fos);
    }

    private boolean filteredOut(String msg) {
        return
                msg.isBlank()
                || msg.startsWith("Progress (") // Maven progress
                || msg.startsWith("Downloaded from") // Download result from Maven
                || msg.startsWith("Downloading from"); // Download message from Maven

    }

    public void splitAndExecute(String taskId, File wd, String command) {
        LOGGER.infof("Executing task %s : %s", taskId, command);
        File log = new File(wd, taskId + ".log");
        try (FileOutputStream fos = new FileOutputStream(log)) {
            int res = initExecutor(taskId, wd, fos)
                    .commandSplit(command)
                    .execute()
                    .getExitValue();

            ensureSuccess(res);
        } catch (Exception e) {
            LOGGER.errorf("Unable to executed `%s`", String.join(" ", command), e);
            throw new RuntimeException(e);
        }
    }

    private void ensureSuccess(int res) {
        if (res != 0) {
            LOGGER.errorf("Command execution failed with result %d", res);
            throw new RuntimeException("Command execution failed with result " + res);
        }
    }


}
