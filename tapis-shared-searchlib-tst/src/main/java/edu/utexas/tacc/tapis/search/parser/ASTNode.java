package edu.utexas.tacc.tapis.search.parser;

/*
 * Class representing a node in the AST
 */
public abstract class ASTNode {
  // Count number of nodes for tree from this point and below
  public int countLeaves()
  {
    if (this instanceof ASTLeaf) return 1;
    else if (this instanceof ASTUnaryExpression)
      return ((ASTUnaryExpression) this).getNode().countLeaves();
    else if (this instanceof ASTBinaryExpression)
      return ((ASTBinaryExpression) this).getLeft().countLeaves() + ((ASTBinaryExpression) this).getRight().countLeaves();
    return 0;
  }
}
