// Need to be in this package to use PassFactory
package com.google.javascript.jscomp;

import com.google.common.base.*;
import com.google.common.io.*;
import com.google.javascript.jscomp.Compiler;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.json.*;
import org.kohsuke.args4j.*;
import org.kohsuke.args4j.spi.*;

class Flags {
  @Option(
    name = "--help",
    aliases = { "-h" },
    handler = BooleanOptionHandler.class,
    usage = "Displays this message")
  boolean showHelp = false;

  @Option(
    name = "--check",
    aliases = { "-c" },
    handler = BooleanOptionHandler.class,
    usage = "Run checks (some checks only work with --optimize)")
  boolean checkTypes = false;

  // Note that some checks (like @const checks) only work when the
  // compiler does the additional work required for optimizations
  @Option(
    name = "--optimize",
    aliases = { "-o" },
    handler = BooleanOptionHandler.class,
    usage = "Optimize and minify (default just links to sources)")
  boolean optimizedBuild = false;

  @Option(
    name = "--watch",
    aliases = { "-w" },
    handler = BooleanOptionHandler.class,
    usage = "Builds every time a file changes, implies --check")
  boolean watchFiles = false;

  @Option(
    name = "--no-warnings",
    aliases = { "-e" },
    handler = BooleanOptionHandler.class,
    usage = "Treat warnings as errors")
  boolean disableWarnings = false;

  @Option(
    name = "--project",
    aliases = { "-p" },
    metaVar = "FILE",
    handler = StringOptionHandler.class,
    usage = "The project file (defaults to project.json)")
  String projectFile = "project.json";
}

interface Define {
  void apply(CompilerOptions options);
}

class ProjectDescription {
  String target; // Equivalent to compiler.jar's "--js_output_file" flag
  String[] sources; // Equivalent to compiler.jar's "--js" flag
  String[] externs; // Equivalent to compiler.jar's "--externs" flag
  Define[] defines; // Equivalent to compiler.jar's "--define" flag
  String[] before; // Commands to run before compilation
  String[] after; // Commands to run after compilation
  String wrapper; // Equivalent to compiler.jar's "--output_wrapper" flag
  long[] lastSourceTimes;
  long[] lastExternTimes;

  ProjectDescription(JSONObject contents) throws JSONException {
    target = contents.getString("target");
    sources = Globals.parseStrings(contents, "sources");
    externs = Globals.parseStrings(contents, "externs");
    before = Globals.parseStrings(contents, "before");
    after = Globals.parseStrings(contents, "after");
    wrapper = contents.optString("wrapper", "%output%");
    lastSourceTimes = new long[sources.length];
    lastExternTimes = new long[externs.length];

    if (contents.has("defines")) {
      JSONObject object = contents.getJSONObject("defines");
      String[] names = JSONObject.getNames(object);
      defines = new Define[names.length];
      for (int i = 0; i < defines.length; i++) {
        defines[i] = parseDefine(object, names[i]);
      }
    } else {
      defines = new Define[0];
    }
  }

  static Define parseDefine(JSONObject object, final String name) throws JSONException {
    final Object property = object.get(name);
    if (property instanceof Boolean) {
      final boolean data = object.getBoolean(name);
      return new Define() {
        @Override
        public void apply(CompilerOptions options) {
          options.setDefineToBooleanLiteral(name, data);
        }
      };
    } else if (property instanceof Number) {
      final double data = object.getDouble(name);
      return new Define() {
        @Override
        public void apply(CompilerOptions options) {
          options.setDefineToDoubleLiteral(name, data);
        }
      };
    } else {
      final String data = object.getString(name);
      return new Define() {
        @Override
        public void apply(CompilerOptions options) {
          options.setDefineToStringLiteral(name, data);
        }
      };
    }
  }
}

class Globals {
  static String[] parseStrings(JSONObject json, String key) throws JSONException {
    if (!json.has(key)) {
      return new String[0];
    }
    JSONArray array = json.getJSONArray(key);
    String[] strings = new String[array.length()];
    for (int i = 0; i < strings.length; i++) {
      strings[i] = array.getString(i);
    }
    return strings;
  }

  // Manual implementation of relative paths because Java sucks
  static String relativePath(String path, String relativeDir) {
    // Skip past common parts
    String[] pathParts = new File(path).getAbsolutePath().split(File.separator);
    String[] relativeParts = new File(relativeDir).getAbsolutePath().split(File.separator);
    int i = 0;
    while (i < pathParts.length && i < relativeParts.length && pathParts[i].equals(relativeParts[i])) {
      i++;
    }

    // Back out of relative directory
    String result = "";
    for (int j = i; j < relativeParts.length; j++) {
      result += ".." + File.separator;
    }

    // Step into path
    for (int j = i; j < pathParts.length; j++) {
      if (j > i) {
        result += File.separator;
      }
      result += pathParts[j];
    }

    return result;
  }

  static void outputColorData(String text) {
    // Only output color data in TTY mode
    Console console = System.console();
    if (console != null) {
      PrintWriter writer = console.writer();
      writer.print(text);
      writer.flush();
    }
  }

  static void grayColor() {
    outputColorData("\033[90m");
  }

  static void redColor() {
    outputColorData("\033[91m");
  }

  static void greenColor() {
    outputColorData("\033[92m");
  }

  static void yellowColor() {
    outputColorData("\033[93m");
  }

  static void resetColor() {
    outputColorData("\033[0m");
  }
}

class CustomPassConfig extends DefaultPassConfig {
  Flags flags;

  static final PassFactory optimizeWebGL = new PassFactory("optimizeWebGL", true) {
    @Override
    CompilerPass create(AbstractCompiler compiler) {
      return new OptimizeWebGLPass(compiler);
    }
  };

  static final PassFactory peepholeOptimize = new PassFactory("peepholeOptimize", false) {
    @Override
    CompilerPass create(AbstractCompiler compiler) {
      return new PeepholeOptimizePass(compiler);
    }
  };

  CustomPassConfig(CompilerOptions options, Flags flags) {
    super(options);
    this.flags = flags;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    ArrayList<PassFactory> optimizations = new ArrayList<PassFactory>();
    optimizations.add(optimizeWebGL);
    optimizations.addAll(super.getOptimizations());
    insertAfter(optimizations, "peepholeOptimizations", peepholeOptimize);
    insertAfter(optimizations, "latePeepholeOptimizations", peepholeOptimize);
    return optimizations;
  }

  static void insertAfter(ArrayList<PassFactory> factories, String name, PassFactory factory) {
    int i = 0;
    while (i < factories.size()) {
      if (factories.get(i++).getName().equals(name)) {
        factories.add(i++, factory);
      }
    }
  }
}

enum ErrorType {
  ERROR,
  WARNING
}

public class ClosureCompilerBuilder {
  Flags flags;
  long buildStartTime;
  long lastProjectTime;
  boolean alreadyShowedPopup;
  ProjectDescription project;
  boolean latestBuildSucceeded;

  static final DiagnosticType ERROR = DiagnosticType.error("ERROR", "{0}");
  static final List<SourceFile> defaultExterns = new ArrayList<SourceFile>();

  static {
    defaultExterns.add(SourceFile.fromCode("console_log.js",
      "var console = {};" +
      "/** @type {function(...)} */ console.log;"));

    try {
      defaultExterns.addAll(CommandLineRunner.getDefaultExterns());
    } catch (IOException e) {
    }
  }

  ClosureCompilerBuilder(Flags flags) {
    this.flags = flags;

    // Watching implies checking (otherwise, why are you watching?)
    if (flags.watchFiles) {
      flags.checkTypes = true;
    }
  }

  void parseProject() {
    // Project should be null if we fail to load it
    project = null;

    // Read the file
    String contents;
    try {
      contents = Files.toString(new File(flags.projectFile), Charsets.UTF_8);
    } catch (IOException e) {
      reportError(e);
      return;
    }

    // Parse the JSON
    try {
      project = new ProjectDescription(new JSONObject(contents));
    } catch (JSONException e) {
      reportError("Could not parse " + flags.projectFile + ": " + e.getMessage());
    }
  }

  void buildProject() {
    // Go through list of commands to run before compilation
    if (!runCommands(project.before)) {
      reportFailure();
      return;
    }

    // Fast builds just link to the sources
    if (!flags.optimizedBuild) {
      if (!createLinkedTargetFile()) {
        reportFailure();
        return;
      }
      if (!flags.checkTypes) {
        reportSuccess();
        return;
      }
    } else {
      removeTargetFile();
    }

    // Initialize the compiler
    Compiler compiler = new Compiler();
    List<SourceFile> externs = new ArrayList<SourceFile>(defaultExterns);
    JSModule module = new JSModule("target");
    for (String extern : project.externs) {
      externs.add(SourceFile.fromFile(extern, Charsets.UTF_8));
    }
    for (String source : project.sources) {
      module.add(SourceFile.fromFile(source, Charsets.UTF_8));
    }

    // Initialize compiler options
    CompilerOptions options = new CompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    for (Define define : project.defines) {
      define.apply(options);
    }
    options.ideMode = !flags.optimizedBuild;
    options.setTrustedStrings(true);

    // Run the compiler without printing anything
    Compiler.setLoggingLevel(Level.OFF);
    compiler.setPassConfig(new CustomPassConfig(options, flags));
    compiler.setErrorManager(new BasicErrorManager() {
      @Override
      public void println(CheckLevel level, JSError error) {}

      @Override
      protected void printSummary() {}
    });
    Result result = compiler.compileModules(externs, Arrays.asList(module), options);

    // Report diagnostics
    for (JSError error : result.errors) {
      reportError(error, ErrorType.ERROR);
    }
    for (JSError warning : result.warnings) {
      reportError(warning, ErrorType.WARNING);
    }

    // Finish the build, including the list of commands to run after compilation
    if (result.errors.length > 0 ||
        flags.disableWarnings && result.warnings.length > 0 ||
        flags.optimizedBuild && !createOptimizedTargetFile(compiler) ||
        !runCommands(project.after)) {
      reportFailure();
    } else {
      reportSuccess();
    }
  }

  boolean runCommands(String[] commands) {
    for (String command : commands) {
      try {
        int exitCode = Runtime.getRuntime().exec(command).waitFor();
        if (exitCode != 0) {
          reportError("Unexpected exit code " + exitCode + ": " + command);
          return false;
        }
      } catch (IOException e) {
        reportError(e);
        return false;
      } catch (InterruptedException e) {
        reportError(e);
        return false;
      }
    }
    return true;
  }

  void poll(boolean isFirstPoll) {
    // Reset build state
    buildStartTime = new Date().getTime();
    latestBuildSucceeded = false;
    alreadyShowedPopup = false;

    // Check the project file for updates
    long projectTime = new File(flags.projectFile).lastModified();
    boolean shouldReportChanges = !isFirstPoll;
    boolean changed = false;
    if (isFirstPoll || projectTime != lastProjectTime) {
      if (shouldReportChanges) {
        reportChange(flags.projectFile);
        shouldReportChanges = false;
      }
      parseProject();
      changed = true;
      lastProjectTime = projectTime;
    }

    // Check the other files for updates
    if (project != null) {
      // Check sources
      for (int i = 0; i < project.sources.length; i++) {
        long sourceTime = new File(project.sources[i]).lastModified();
        if (!changed && project.lastSourceTimes[i] != sourceTime) {
          if (shouldReportChanges) {
            reportChange(project.sources[i]);
            shouldReportChanges = false;
          }
          changed = true;
        }
        project.lastSourceTimes[i] = sourceTime;
      }

      // Check externs
      for (int i = 0; i < project.externs.length; i++) {
        long externTime = new File(project.externs[i]).lastModified();
        if (!changed && project.lastExternTimes[i] != externTime) {
          if (shouldReportChanges) {
            reportChange(project.externs[i]);
            shouldReportChanges = false;
          }
          changed = true;
        }
        project.lastExternTimes[i] = externTime;
      }

      // Build again when files are changed
      if (isFirstPoll || changed) {
        buildProject();
      }
    }
  }

  void run() {
    // Try to build at least once
    poll(true);

    // When not watching, use the exit code to indicate success
    if (!flags.watchFiles) {
      System.exit(latestBuildSucceeded ? 0 : 1);
    }

    // Watch files by polling repeatedly
    while (true) {
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        break;
      }

      // Catch internal compiler errors
      try {
        poll(false);
      } catch (Throwable t) {
        showPopup(t.getMessage(), null, 0);
        t.printStackTrace();
      }
    }
  }

  boolean createLinkedTargetFile() {
    PrintWriter writer;
    try {
      writer = new PrintWriter(project.target);
    } catch (FileNotFoundException e) {
      reportError(e);
      return false;
    }
    String targetDir = new File(project.target).getAbsoluteFile().getParent();
    for (String source : project.sources) {
      String path = Globals.relativePath(source, targetDir);
      writer.println("document.write('<script src=\"" + path + "\"></script>');");
    }
    writer.close();
    return true;
  }

  boolean createOptimizedTargetFile(Compiler compiler) {
    PrintWriter writer;
    try {
      writer = new PrintWriter(project.target);
    } catch (FileNotFoundException e) {
      reportError(e);
      return false;
    }
    writer.println(project.wrapper.replace("%output%", compiler.toSource()));
    writer.close();
    return true;
  }

  void removeTargetFile() {
    try {
      new File(project.target).delete();
    } catch (Exception e) {
    }
  }

  long elapsedBuildTime() {
    return new Date().getTime() - buildStartTime;
  }

  void reportChange(String file) {
    reportStatus("\nDetected change: " + file);
  }

  void reportStatus(String text) {
    Globals.grayColor();
    System.out.println(text);
    Globals.resetColor();
  }

  void reportFailure() {
    Globals.redColor();
    System.out.print("Failure");
    reportTime(elapsedBuildTime());
  }

  void reportSuccess() {
    Globals.greenColor();
    System.out.print("Success");
    reportTime(elapsedBuildTime());
    latestBuildSucceeded = true;
  }

  void reportTime(long time) {
    reportStatus(" (" + (time + 50) / 100 / 10.0 + " seconds)");
  }

  void reportError(Exception exception) {
    reportError(exception.getMessage());
  }

  void reportError(String text) {
    reportError(JSError.make(ERROR, text), ErrorType.ERROR);
  }

  void reportError(JSError error, ErrorType errorType) {
    // Describe where the location occurred
    String location =
      error.sourceName == null || error.sourceName.length() == 0 ? "" :
      error.lineNumber == -1 ? " (in " + error.sourceName + ")" :
      " (line " + error.lineNumber + " of " + error.sourceName + ")";

    // Try to report errors using the OS if we're in watch mode, but only
    // show the popup in watch mode and only for the first error/warning
    if (flags.watchFiles && !alreadyShowedPopup) {
      showPopup(error.description + location, error.sourceName, error.lineNumber);
      alreadyShowedPopup = true;
    }

    // Always print stuff to the console
    String[] parts = error.description.split("\n", 2);
    if (errorType == ErrorType.ERROR) {
      Globals.redColor(); System.out.print("Error: ");
    } else {
      Globals.yellowColor(); System.out.print("Warning: ");
    }
    Globals.resetColor(); System.out.print(parts[0]);
    Globals.grayColor(); System.out.println(location);
    if (parts.length > 1) {
      System.out.println(parts[1]);
    }
    Globals.resetColor();
  }

  void showPopup(String text, String file, int line) {
    String TERMINAL_NOTIFIER_PATH = "../../../../../node_modules/terminal-notifier/" +
      "terminal-notifier.app/Contents/MacOS/terminal-notifier";
    String path = ClosureCompilerBuilder.class.getResource("ClosureCompilerBuilder.class").getPath();
    path = new File(new File(path).getAbsoluteFile().getParent(), TERMINAL_NOTIFIER_PATH).getAbsolutePath();
    try {
      Runtime.getRuntime().exec(new String[] {
        path,
        "-title", "Google Closure Compiler",
        "-group", "closure-compiler-builder",
        "-message", text,
        "-execute", getEditorOpenCommand(file, line)
      });
    } catch (IOException e) {
      // Fall back to a terminal beep if terminal-notifier fails
      System.out.print("\007");
    }
  }

  static String getEditorOpenCommand(String file, int line) {
    // Note: This doesn't work if the file path contains a single quote
    if (file == null) {
      return "";
    }
    String editor = System.getenv().get("EDITOR");
    file = new File(file).getAbsolutePath();
    if (editor == null) {
      return "open '" + file + "'";
    }
    return editor + " '" + file + ":" + line + "'";
  }

  static void usage(CmdLineParser parser) {
    System.out.println();
    System.out.println("Options:");
    parser.printUsage(System.out);
    System.out.println();
    System.out.println("Project format (JSON):");
    System.out.println("{");
    System.out.println("  // Required");
    System.out.println("  \"target\": \"compiled.js\",");
    System.out.println("  \"sources\": [\"foo.js\", \"bar.js\"],");
    System.out.println("");
    System.out.println("  // Optional");
    System.out.println("  \"externs\": [\"jquery.externs.js\"],");
    System.out.println("  \"before\": [\"before.sh\"],");
    System.out.println("  \"after\": [\"after.sh\"],");
    System.out.println("  \"defines\": { \"LOGGING\": true },");
    System.out.println("  \"wrapper\": \"(function() {%output%})();\"");
    System.out.println("}");
    System.out.println();
  }

  public static void main(String[] args) {
    Flags flags = new Flags();
    CmdLineParser parser = new CmdLineParser(flags);

    try {
      parser.parseArgument(args);
      if (flags.showHelp) {
        usage(parser);
      } else {
        // Catch internal compiler errors
        try {
          new ClosureCompilerBuilder(flags).run();
        } catch (Throwable t) {
          t.printStackTrace();

          // Need to explicitly exit because of extra threads
          System.exit(1);
        }
      }
    } catch (CmdLineException e) {
      System.out.println(e.getMessage());
      usage(parser);
      System.exit(1);
    }

    // Need to explicitly exit because of extra threads
    System.exit(0);
  }
}
