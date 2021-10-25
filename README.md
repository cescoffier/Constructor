# The Constructor Project

One-Liner: Build projects.

_Rationale_: The Quarkus project is based on Eclipse Vert.x. 
But between Vert.x and Quarkus, there are multiple projects to build before attempting an integration in Quarkus.
Providing feedback to the Vert.x team is a long and hard process. 
For each project, you need to update the dependencies, then build, then switch to the next project, adapt the dependencies, and so on until you reach Quarkus.
Constructor is a tool to help you build that chain.
You list the repositories that need to be built, and it automatically adapts the dependencies and build them. 

## Configuration of the pipeline

Constructor is a command line tool taking as parameter a `yaml` file (default: `constructor.yaml`).
This file lists the project to build. 
For example:

```yaml
versions:
  vertx: 4.2.0.Beta1
pipelines:
  - name: main-build
      - repository: smallrye/smallrye-mutiny-vertx-bindings
        branchOrCommit: vertx.4.2.0.Beta1
        version: 2.14.0-SNAPSHOT
        commands:
          - mvn clean install
        dependencies:
           vertx.version: vertx
      - repository: smallrye/smallrye-reactive-messaging
        branchOrCommit: main
        version: 3.10.0-SNAPSHOT
        dependencies:
          vertx.version: vertx
          "smallrye-vertx-mutiny-clients.version": smallrye/smallrye-mutiny-vertx-bindings
      - repository: quarkusio/quarkus-http
        branchOrCommit: main
        version: 4.1.2-SNAPSHOT
        dependencies:
          vertx.version: vertx
      - repository: quarkusio/quarkus
        branchOrCommit: main
        version: 999-SNAPSHOT
        dependencies:
          vertx.version: vertx
          "smallrye-reactive-messaging.version": smallrye/smallrye-reactive-messaging
          "smallrye-mutiny-vertx-binding.version": smallrye/smallrye-mutiny-vertx-bindings
        commands:
          - mvn clean install -Dquickly
```

### Versions

`version` is a dictionary containing `name -> version pair`. 
These entries will be used to resolve versions in the projects.

### Pipelines

`pipelines` is the set of _pipeline_ (ordered group of steps) you want to build.
A pipeline has a name, and can have either a set of steps, or a stored in an external file.
In that case, import is as follows:

```yaml
pipelines:  
   - name: vert.x
     file: vertx.yaml
   - name: main
     steps: #... 
```

The imported pipeline is just a set of steps:

```yaml
name: vert.x
steps:
 - repository: eclipse-vertx/vert.x
   branchOrCommit: master
   commands:
        - mvn clean install -DskipTests
  #...
```

The imported file path is resolved relatively from the main constructor file.

### Steps (projects)

`steps` is the list of project to build in the pipeline.
The order matters as constructor builds them in this order.

Each step's entry is a project and contains:

- the repository (mandatory) - the identifier of the GitHub repository (`organization/repository`)
- the branch or commit (default to the default branch) - the branch or commit to use.
- the version - the expected version, required for resolving dependencies between projects
- the commands - a sequence of shell command to run. Default is `mvn clean install -DskipTests -DskipITs`
- the dependencies - a dictionary of `variable|g:a -> repository|version`. 

The _dependencies_ section is where the magic happens.
Each entry instructs constructor to adapt the project.
The key is either the name of a variable used in the project's pom.xml file or the _groupid:artifactId_ of a dependency.
The value is either the name of a repository or a key from the global `versions` dictionary. 
Constructor resolves each entry and adapts the project's `pom.xml`.

## The CLI

The constructor CLI is a Quarkus application.

```
Usage: constructor [-hV] [-r=<repo>] [-w=<work>] <constructor-file>
      <constructor-file>   The constructor pipeline description
  -h, --help               Show this help message and exit.
  -r, --local-repository=<repo>
                           The local repository directory
  -V, --version            Print version information and exit.
  -w, --work-dir=<work>    The working directory
```

**IMPORTANT**: All projects are build from a local maven repository which is not you `~/.m2/repository`. 

## FAQ

### Is Constructor only for Vert.x and Quarkus?

No, you can build any chain, as soon as the projects you need to build are using Maven as build tools.

### Why only Maven?

Maven proposes easy ways to update dependencies and variables.
Check the [maven-versions-plugin](https://www.mojohaus.org/versions-maven-plugin/) for detail.

### How to run my pipeline offline

At the moment you will need to build Constructor on your machine.

```shell
git clone https://github.com/cescoffier/Constructor constructor
cd constructor/constructor
mvn clean package -DskipTests
```

Then, you can run constructor using:

```shell
java -jar constructor/target/quarkus-app/quarkus-run.jar <path to the pipeline file>
```



