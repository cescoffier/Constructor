package me.escoffier.constructor;

import org.kohsuke.github.*;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "report", mixinStandardHelpOptions = true)
public class ReportCommand implements Runnable {

    @CommandLine.Option(names = "token", description = "Github token to use when calling the Github API")
    private String token;

    @CommandLine.Option(names = "status", description = "The status of the CI run")
    private String status;

    @CommandLine.Option(names = "issueRepo", description = "The repository where the issue resides (i.e. quarkusio/quarkus)")
    private String issueRepo;

    @CommandLine.Option(names = "issueNumber", description = "The issue to update")
    private Integer issueNumber;

    @CommandLine.Option(names = "thisRepo", description = "The repository for which we are reporting the CI status")
    private String thisRepo;

    @CommandLine.Option(names = "runId", description = "The ID of the Github Action run for  which we are reporting the CI status")
    private String runId;

    @Override
    public void run() {
        try {
            final boolean success = "success".equalsIgnoreCase(status);
            if ("cancelled".equalsIgnoreCase(status)) {
                System.out.println("Job status is `cancelled` - exiting");
                System.exit(0);
            }

            System.out.printf("The CI build had status %s.%n", status);

            final GitHub github = new GitHubBuilder().withOAuthToken(token).build();
            final GHRepository repository = github.getRepository(issueRepo);

            final GHIssue issue = repository.getIssue(issueNumber);
            if (issue == null) {
                System.out.printf("Unable to find the issue %s in project %s%n", issueNumber, issueRepo);
                System.exit(-1);
            } else {
                System.out.printf("Report issue found: %s - %s%n", issue.getTitle(), issue.getHtmlUrl().toString());
                System.out.printf("The issue is currently %s%n", issue.getState().toString());
            }

            if (success) {
                if (isOpen(issue)) {
                    // close issue with a comment
                    final GHIssueComment comment = issue.comment(String.format("Build fixed:\n* Link to latest CI run: https://github.com/%s/actions/runs/%s", thisRepo, runId));
                    issue.close();
                    System.out.printf("Comment added on issue %s - %s, the issue has also been closed%n", issue.getHtmlUrl().toString(), comment.getHtmlUrl().toString());
                } else {
                    System.out.println("Nothing to do - the build passed and the issue is already closed");
                }
            } else  {
                if (isOpen(issue)) {
                    final GHIssueComment comment = issue.comment(String.format("The build is still failing:\n* Link to latest CI run: https://github.com/%s/actions/runs/%s", thisRepo, runId));
                    System.out.printf("Comment added on issue %s - %s%n", issue.getHtmlUrl().toString(), comment.getHtmlUrl().toString());
                } else {
                    issue.reopen();
                    final GHIssueComment comment = issue.comment(String.format("Unfortunately, the build failed:\n* Link to latest CI run: https://github.com/%s/actions/runs/%s", thisRepo, runId));
                    System.out.printf("Comment added on issue %s - %s, the issue has been re-opened%n", issue.getHtmlUrl().toString(), comment.getHtmlUrl().toString());
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isOpen(GHIssue issue) {
        return (issue.getState() == GHIssueState.OPEN);
    }

}
