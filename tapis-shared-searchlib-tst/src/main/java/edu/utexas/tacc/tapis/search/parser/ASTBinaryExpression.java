package edu.utexas.tacc.tapis.search.parser;

/*
 * Class representing nodes in the AST
 */
public class ASTBinaryExpression extends ASTNode
{
  private String op;
  private ASTNode left, right;
  ASTBinaryExpression(String o, ASTNode l, ASTNode r)
  {
    op = o;
    left = l;
    right = r;
  }

  public String getOp() { return op; }

  public ASTNode getLeft() { return left; }

  public ASTNode getRight() { return right; }

  public String toString() { return "(" + left + "." + op + "." + right + ")"; }
}