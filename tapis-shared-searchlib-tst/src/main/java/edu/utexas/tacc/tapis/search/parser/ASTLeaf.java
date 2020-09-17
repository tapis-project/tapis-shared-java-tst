package edu.utexas.tacc.tapis.search.parser;

/*
 * Class representing a leaf node in the AST
 * A leaf node contains a value as a string
 */
public class ASTLeaf extends ASTNode
{
  private String value;
  ASTLeaf(String v) { value = v; }
  public String getValue() { return value; }
  public String toString() { return value; }
}
