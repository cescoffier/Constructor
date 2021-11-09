package me.escoffier.constructor.build;

public class ConstructorException extends RuntimeException {

    private final String repo;
    private final String command;


    public ConstructorException(String repo, String command, Exception cause) {
        super(cause);
        this.repo = repo;
        this.command = command;
    }

    @Override
    public String getMessage() {
        String m = "The build of " + repo + "has failed";
        if (command != null) {
            return m + ". The command " + command + " failed with: " + getCause().getMessage();
        } else {
            return m + " with: " + getCause().getMessage();
        }
    }
}
