package me.escoffier.constructor;

import org.jboss.logging.Logger;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;

import javax.enterprise.context.ApplicationScoped;
import java.io.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class Process {

    private static final Logger LOGGER = Logger.getLogger("Process");

    public void execute(String taskId, File wd, String... commands) {
        File log = new File(wd, taskId + ".log");
        try (FileOutputStream fos = new FileOutputStream(log)) {
            int res = new ProcessExecutor()
                    .directory(wd)
                    .readOutput(true)
                    .redirectOutput(new LogOutputStream() {
                        @Override
                        protected void processLine(String s) {
                            LOGGER.infof("[%s] - %s", taskId, s);
                        }
                    })
                    .redirectOutputAlsoTo(fos)
                    .redirectError(new LogOutputStream() {
                        @Override
                        protected void processLine(String s) {
                            LOGGER.warnf("[%s] - %s", taskId, s);
                        }
                    })
                    .redirectErrorAlsoTo(fos)
                    .command(commands)
                    .execute()
                    .getExitValue();

            ensureSuccess(res);

        } catch (Exception e) {
            LOGGER.errorf("Unable to executed `%s`", String.join(" ", commands), e);
            throw new RuntimeException(e);
        }
    }

    public void splitAndExecute(String taskId, File wd, String command) {
        File log = new File(wd, taskId + ".log");
        try (FileOutputStream fos = new FileOutputStream(log)) {
            int res = new ProcessExecutor()
                    .directory(wd)
                    .readOutput(true)
                    .redirectOutput(new LogOutputStream() {
                        @Override
                        protected void processLine(String s) {
                            LOGGER.infof("[%s] - %s", taskId, s);
                        }
                    })
                    .redirectOutputAlsoTo(fos)
                    .redirectError(new LogOutputStream() {
                        @Override
                        protected void processLine(String s) {
                            LOGGER.warnf("[%s] - %s", taskId, s);
                        }
                    })
                    .redirectErrorAlsoTo(fos)
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
