package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.*;
import java.util.*;

/**
 * Finds properties off of uncaptured globals and renames all other instances
 * of those properties. For example, this would rename Vector.prototype.abs
 * even though Math.abs is in the externs because the Math singleton is never
 * captured so the two uses of "abs" don't conflict.
 */
public class CaptureAwareRenamingPass implements CompilerPass {
  AbstractCompiler compiler;

  final String PREFIX = "$CaptureAwareRenamingPass$";

  public CaptureAwareRenamingPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    // Gather global constants
    final HashSet<Var> uncapturedGlobalVars = new HashSet<Var>();
    final HashMap<String, Var> uncapturedGlobalNames = new HashMap<String, Var>();
    NodeTraversal.traverse(compiler, externs, new NodeTraversal.AbstractShallowCallback() {
      @Override
      public void visit(NodeTraversal t, Node node, Node parent) {
        if (node.isVar()) {
          JSDocInfo info = node.getJSDocInfo();
          if (info != null && info.isConstant()) {
            String name = node.getFirstChild().getString();
            Var var = t.getScope().getVar(name);
            uncapturedGlobalVars.add(var);
            uncapturedGlobalNames.put(name, var);
          }
        }
      }
    });

    // Narrow down globals to those that are never captured
    NodeTraversal.traverse(compiler, root, new NodeTraversal.AbstractPostOrderCallback() {
      void capture(NodeTraversal t, Node node) {
        while (node.isComma()) {
          node = node.getLastChild();
        }
        if (node.isName()) {
          String name = node.getString();
          if (t.getScope().getVar(name) == null) {
            Var var = uncapturedGlobalNames.get(name);
            if (var != null) {
              uncapturedGlobalVars.remove(var);
              uncapturedGlobalNames.remove(name);
            }
          }
        }
      }
      @Override
      public void visit(NodeTraversal t, Node node, Node parent) {
        switch (node.getType()) {
          case Token.CALL:
            for (Node n = node.getFirstChild().getNext(); n != null; n = n.getNext()) {
              capture(t, n);
            }
            break;

          case Token.ARRAYLIT:
            for (Node n = node.getFirstChild(); n != null; n = n.getNext()) {
              capture(t, n);
            }
            break;

          case Token.OBJECTLIT:
            for (Node n = node.getFirstChild(); n != null; n = n.getNext()) {
              Preconditions.checkState(n.isStringKey());
              capture(t, n.getFirstChild());
            }
            break;

          case Token.ASSIGN:
            capture(t, node.getLastChild());
            break;

          case Token.VAR:
            for (Node n = node.getFirstChild(); n != null; n = n.getNext()) {
              Preconditions.checkState(n.isName());
              if (n.getFirstChild() != null) {
                capture(t, n.getFirstChild());
              }
            }
            break;
        }
      }
    });

    // Find properties off of uncaptured globals
    final HashSet<String> change = new HashSet<String>();
    final HashSet<String> keep = new HashSet<String>();
    NodeTraversal.traverse(compiler, externs, new NodeTraversal.AbstractShallowCallback() {
      @Override
      public void visit(NodeTraversal t, Node node, Node parent) {
        if (node.isGetProp()) {
          String property = node.getLastChild().getString();
          if (node.getFirstChild().isName()) {
            String object = node.getFirstChild().getString();
            if (uncapturedGlobalVars.contains(t.getScope().getVar(object))) {
              // Remember that we may be able to change this property. We
              // may only change it if we don't find a reason to keep it.
              change.add(property);
              return;
            }
          }

          // Properties not off of uncaptured globals force us to stay fixed no
          // matter what. For example, if we want to rename all instances of
          // "abs" other than Math.abs but there is also Node.prototype.abs in
          // the externs, we must keep "abs" the same and not rename it.
          keep.add(property);
        }
      }
    });

    // Just rename those properties when they are not off of uncaptured globals
    NodeTraversal.traverse(compiler, root, new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node node, Node parent) {
        if (node.isGetProp()) {
          String property = node.getLastChild().getString();

          // Only rename something if we never found a reason to keep it
          if (change.contains(property) && !keep.contains(property)) {

            // Don't rename things on uncaptured globals
            if (node.getFirstChild().isName()) {
              String name = node.getFirstChild().getString();
              if (t.getScope().getVar(name) == null &&
                  uncapturedGlobalNames.containsKey(name)) {
                return;
              }
            }

            node.getLastChild().setString(PREFIX + property);
          }
        }
      }
    });
  }
}
