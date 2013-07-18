package com.google.javascript.jscomp;

import com.google.javascript.jscomp.Compiler;
import java.util.*;

public class Tests {
  static void check(String input, String output) {
    List<SourceFile> externs = new ArrayList<SourceFile>(ClosureCompilerBuilder.DEFAULT_EXTERNS);
    List<SourceFile> sources = new ArrayList<SourceFile>();
    List<Define> defines = new ArrayList<Define>();
    Flags flags = new Flags();
    flags.optimizedBuild = true;
    sources.add(SourceFile.fromCode("input.js", input));

    Compiler compiler = new Compiler();
    Result result = ClosureCompilerBuilder.compile(compiler, externs, sources, defines, flags);
    for (JSError error : result.errors) {
      System.out.println("Error: " + error.description);
    }
    for (JSError warning : result.warnings) {
      System.out.println("Warning: " + warning.description);
    }

    String code = compiler.toSource();
    if (!code.equals(output)) {
      System.out.println("Expected \"" + output + "\" but got \"" + code + "\"");
      System.exit(1);
    } else {
      System.out.println("Test passed");
    }
  }

  static void run() {
    System.out.println("Running tests");
    testCaptureAwareRenamingPass();
    System.out.println("All tests passed");
  }

  static void testCaptureAwareRenamingPass() {
    check(
      "var ns = {};" +
      "ns.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "console.log(Math, ns.abs(-1));",
      "console.log(Math,Math.abs(-1));");

    check(
      "/** @constructor */" +
      "function Foo() {}" +
      "Foo.prototype.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "console.log(new Foo().abs(-1));",
      "console.log(Math.abs(-1));");

    check(
      "/** @constructor */" +
      "function Foo() {}" +
      "Foo.prototype.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "console.log(Math, new Foo().abs(-1));",
      "function a(){}" +
      "a.prototype.abs=function(b){return Math.abs(b)};" +
      "console.log(Math,(new a).abs(-1));");

    check(
      "/** @constructor */" +
      "function Foo() {}" +
      "Foo.prototype.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "var capture = Math;" +
      "console.log(capture, new Foo().abs(-1));",
      "function a(){}" +
      "a.prototype.abs=function(b){return Math.abs(b)};" +
      "console.log(Math,(new a).abs(-1));");

    check(
      "/** @constructor */" +
      "function Foo() {}" +
      "Foo.prototype.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "var capture = (console.log(), Math);" +
      "console.log(capture, new Foo().abs(-1));",
      "function a(){}" +
      "a.prototype.abs=function(b){return Math.abs(b)};" +
      "var c=(console.log(),Math);" +
      "console.log(c,(new a).abs(-1));");

    check(
      "/** @constructor */" +
      "function Foo() {}" +
      "Foo.prototype.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "var capture = [Math];" +
      "console.log(capture, new Foo().abs(-1));",
      "function a(){}" +
      "a.prototype.abs=function(b){return Math.abs(b)};" +
      "console.log([Math],(new a).abs(-1));");

    check(
      "/** @constructor */" +
      "function Foo() {}" +
      "Foo.prototype.abs = function(x) {" +
      "  return Math.abs(x);" +
      "};" +
      "var capture = { capture: Math };" +
      "console.log(capture, new Foo().abs(-1));",
      "function a(){}" +
      "a.prototype.abs=function(b){return Math.abs(b)};" +
      "console.log({capture:Math},(new a).abs(-1));");
  }
}
