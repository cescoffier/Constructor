package me.escoffier.constructor;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(mixinStandardHelpOptions = true, subcommands = {BuildCommand.class, ReportCommand.class})
public class ConstructorCommand {
}
