package com.google.javascript.jscomp;

import com.google.javascript.rhino.*;

public class PeepholeOptimizePass extends NodeTraversal.AbstractPostOrderCallback implements CompilerPass {
  AbstractCompiler compiler;

  PeepholeOptimizePass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal nt, Node node, Node parent) {
    int type = node.getType();

    // (0 | 0 * foo) == 0 no matter what value foo contains
    if (type == Token.MUL && willConvertOperandsToInt32(parent)) {
      Node A = node.getFirstChild();
      Node B = A.getNext();
      Double a = NodeUtil.getNumberValue(A);
      Double b = NodeUtil.getNumberValue(B);
      if (a != null && a == 0 && !NodeUtil.mayHaveSideEffects(B) ||
          b != null && b == 0 && !NodeUtil.mayHaveSideEffects(A)) {
        parent.replaceChild(node, NodeUtil.numberNode(0, node));
        compiler.reportCodeChange();
      }
    }
  }

  static boolean willConvertOperandsToInt32(Node node) {
    int type = node.getType();
    return
      type == Token.ASSIGN_BITOR ||
      type == Token.ASSIGN_BITXOR ||
      type == Token.ASSIGN_BITAND ||
      type == Token.ASSIGN_LSH ||
      type == Token.ASSIGN_RSH ||
      type == Token.ASSIGN_URSH ||
      type == Token.BITOR ||
      type == Token.BITXOR ||
      type == Token.BITAND ||
      type == Token.LSH ||
      type == Token.RSH ||
      type == Token.URSH;
  }
}
