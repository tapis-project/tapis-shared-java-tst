package edu.utexas.tacc.tapis.search.parser;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 *  Basic tests of the generated ASTParser.
 * 
 * @author scblack
 */
@Test(groups= {"unit"})
public class ASTParserTest
{

  public static final String sysNamePrefix = "TestSys";
  public static final String testSuiteNameKey = "SrchEndpoint";

  private static final String BIG_QUERY =
     "enabled = 'true' AND (owner = 'jdoe' OR proxy_port > 1024) OR " +
     "owner = 'a' AND (owner = 'b' OR port > 1) OR (owner = 'c' AND port < 9) OR " +
     "owner = 'a1' AND (owner = 'b1' OR port > 1) OR (owner = 'c1' AND port < 9) OR " +
     "owner = 'a2' AND (owner = 'b2' OR port > 1) OR (owner = 'c2' AND port < 9) OR " +
     "owner = 'a3' AND (owner = 'b3' OR port > 1) OR (owner = 'c3' AND port < 9) OR " +
     "owner = 'a4' AND (owner = 'b4' OR port > 1) OR (owner = 'c4' AND port < 9) OR " +
     "owner = 'a5' AND (owner = 'b5' OR port > 1) OR (owner = 'c5' AND port < 9) OR " +
     "owner = 'a6' AND (owner = 'b6' OR port > 1) OR (owner = 'c6' AND port < 9) OR " +
     "owner = 'a7' AND (owner = 'b7' OR port > 1) OR (owner = 'c7' AND port < 9) OR " +
     "owner = 'a8' AND (owner = 'b8' OR port > 1) OR (owner = 'c8' AND port < 9) OR " +
     "owner = 'a9' AND (owner = 'b9' OR port > 1) OR (owner = 'c9' AND port < 9) OR " +
     "owner = 'a0' AND (owner = 'b0' OR port > 1) OR (owner = 'c0' AND port < 9)";
  private static final String DEEP_NEST =
     "(" +
       "enabled = 'true' AND (owner = 'jdoe' OR proxy_port > 1024) OR " +
       "(owner = 'a' AND (owner = 'b' OR port > 1) OR (owner = 'c' AND port < 9)) OR" +
       "(" +
         "(owner = 'a1' AND (owner = 'b1' OR port > 1) OR (owner = 'c1' AND port < 9)) OR " +
         "(owner = 'a2' AND (owner = 'b2' OR port > 1) OR (owner = 'c2' AND port < 9))" +
       ") OR" +
       "(" +
         "(" +
           "(owner = 'a3' AND (owner = 'b3' OR port > 1) OR (owner = 'c3' AND port < 9)) OR" +
           "(owner = 'a4' AND (owner = 'b4' OR port > 1) OR (owner = 'c4' AND port < 9))" +
         ") OR" +
         "(" +
           "(owner = 'a5' AND (owner = 'b5' OR port > 1) OR (owner = 'c5' AND port < 9)) OR" +
           "(owner = 'a6' AND (owner = 'b6' OR port > 1) OR (owner = 'c6' AND port < 9))" +
         ")" +
       ")" +
     ")";

  /*
   * Check ASTParser.parse() - valid cases
   */
  @Test(groups = {"unit"})
  public void testASTParseValid()
  {
    // Create all input and validation data for tests
    String sys1Name = sysNamePrefix + "_" + testSuiteNameKey + "_" + String.format("%03d", 1);
    // Inputs
    var validCaseInputs = new HashMap<Integer, CaseData>();
    validCaseInputs.put(  1,new CaseData(2, "name = '" + sys1Name + "'"));
    validCaseInputs.put(  2,new CaseData(2, "enabled = 'true'"));
    validCaseInputs.put(  3,new CaseData(2, "enabled <> 'true'"));
    validCaseInputs.put(  4,new CaseData(2, "port < 7"));
    validCaseInputs.put(  5,new CaseData(2, "(port <= 7)"));
    validCaseInputs.put(  6,new CaseData(2, "(port > 7)"));
    validCaseInputs.put(  7,new CaseData(2, "(port >= 7)"));
    validCaseInputs.put(  8,new CaseData(2, "name LIKE 'testname%'"));
    validCaseInputs.put(  9,new CaseData(2, "name NOT LIKE 'testname_'"));
    validCaseInputs.put( 10,new CaseData(2, "port BETWEEN '0' AND '1024'"));
    validCaseInputs.put( 11,new CaseData(2, "port NOT BETWEEN '0' AND '1024'"));
    validCaseInputs.put( 12,new CaseData(2, "owner IN ('jdoe', 'msmith')"));
    validCaseInputs.put( 13,new CaseData(2, "owner NOT IN ('jdoe', 'msmith')"));
    validCaseInputs.put( 20,new CaseData(2, "owner IN ('jdoe')"));
    validCaseInputs.put( 21,new CaseData(2, "owner IN ('a','b','c')"));
    validCaseInputs.put( 22,new CaseData(2, "owner IN ('jdoe','msmith','jsmith','mdoe','a','b','c')"));
    validCaseInputs.put(101,new CaseData(4, "host = 'stampede2.tacc.utexas.edu' AND owner = 'jdoe'"));
    validCaseInputs.put(102,new CaseData(4, "host = 'stampede2.tacc.utexas.edu' OR owner = 'jdoe'"));
    validCaseInputs.put(103,new CaseData(6, "enabled = 'true' AND (owner = 'jdoe' OR proxy_port > 1024)"));
    validCaseInputs.put(104,new CaseData(10, "owner = 'a' AND (owner = 'b' OR port > 1) OR (owner = 'c' AND port < 9)"));
    validCaseInputs.put(201,new CaseData(116, BIG_QUERY));
    validCaseInputs.put(202,new CaseData(76, DEEP_NEST));

    // Iterate over test cases
    for (Map.Entry<Integer, CaseData> item : validCaseInputs.entrySet())
    {
      CaseData ci = item.getValue();
      int caseNum = item.getKey();
      System.out.println("Checking case # " + caseNum + " Input: " + ci.sqlStr);
      ASTNode ast = ASTParser.parse(ci.sqlStr);
      Assert.assertNotNull(ast);
      int leafCount = ast.countLeaves();
      System.out.println("  ******* AST = " + ast.toString());
      System.out.println("  ******* AST leaf count = " + ast.countLeaves());
      Assert.assertEquals(leafCount, ci.count);
    }
  }

  /**
   * Case data consists of the case number, expected leaf count and an input string
   */
  static class CaseData
  {
    public final int count; // Number of expected leaf nodes in the AST
    public final String sqlStr;
    CaseData(int r, String s) { count=r; sqlStr =s; }
  }
}
