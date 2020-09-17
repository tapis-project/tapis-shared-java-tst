package edu.utexas.tacc.tapis.search.parser;

import org.apache.commons.lang3.StringUtils;

/*
 * Class representing a node in the AST containing a unary expression
 * A unary node contains an operator and a node
 */
public class ASTUnaryExpression extends ASTNode {
  private String op;
  private ASTNode node;
  
  ASTUnaryExpression(String o, ASTNode n) {op = o; node = n;}

  public String getOp() { return op; }

  public ASTNode getNode() { return node; }

  public String toString()
  {
    if (StringUtils.isBlank(op)) return node.toString();
    else return "." + op + "." + node.toString();
  }
}
