package edu.utexas.tacc.tapis.search;

import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import javax.ws.rs.core.MultivaluedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.testng.Assert.*;

/**
 * Tests for methods in the SearchUtils class.
 */
public class SearchUtilsTest
{
  // multiple escapes in: '\ \\ \\' <1> <2> <2>
  private static final String multiEscapeIn1 = "\\ \\\\ \\\\";
  // multiple escapes in: '\\\ \ \\ \\\' <3> <1> <2> <4>
  private static final String multiEscapeIn2 = "\\\\\\ \\ \\\\ \\\\\\\\";
  // multiple escapes in: '\ \\ \\\ \\\\ \\\\\' <1> <2> <3> <4> <5>
  private static final String multiEscapeIn3 = "\\ \\\\ \\\\\\ \\\\\\\\ \\\\\\\\\\";

  // Zero results
//  private static final String[] zeroResults = new String[] {};
  private static final String zeroResults = null;

  // NOTE: Must statically initialize the input/output maps, otherwise when run using maven maps may be uninitialized.
  // Test data
  // Valid and invalid timestamps in various formats
  private static final String[] validTimestamps =
    { "1800-01-01T00:00:00.123456-00:00",
      "2200-04-29T14:15:52.123456Z",
      "2200-04-29T14:15:52.123456",
      "2200-04-29T14:15:52.123-01:00",
      "2200-04-29T14:15:52.123Z",
      "2200-04-29T14:15:52.123",
      "2200-04-29T14:15:52+05:30",
      "2200-04-29T14:15:52Z",
      "2200-04-29T14:15:52",
      "2200-04-29T14:15+01:00",
      "2200-04-29T14:15Z",
      "2200-04-29T14:15",
      "2200-04-29T14-06:00",
      "2200-04-29T14Z",
      "2200-04-29T14",
      "2200-04-29-06:00",
      "2200-04-29Z",
      "2200-04-29",
      "2200-04+03:00",
      "2200-04Z",
      "2200-04",
      "2200-06:00",
      "2200Z",
      "2200"
    };
  private static final String[] invalidTimestamps =
    { null,
      "",
      "1",
      "12",
      "123",
      "123Z",
      "2200-04-00T14:15:00",
      "2200-04-32T14:15:00",
      "2200-00-29T14:15:00",
      "2200-13-29T14:15:00",
      "2200-04-04T14:15:61",
      "2200-04-04T14:61:00",
      "2200-04-04T25:15:00",
      "00-04-04T14:15:00",
      "22001-04-04T14:15:00",
      "2200-04-29T14,15:52.123-01:00Z",
      "2200-04-29T14:15:523.123Z",
      "2200-04-29T14:156:52.123",
      "2200-04-29T14:15:52+005:30",
      "2200-04-29T14:15:52+05:300",
      "2200-04-29T14:15:52z05:30",
      "2200-04-29X14:15:52Z",
      "2200-04-291T14:15:52",
      "2200-04-29 14:15:52",
    };

  // Create input and validation data for valid test cases
  // Inputs
  private static final Map<Integer, CaseInputData> validCaseInputs;
  static
  {
    validCaseInputs = Map.ofEntries(
            Map.entry(1, new CaseInputData(1, "enabled.eq.true")),
            Map.entry(2, new CaseInputData(1, "(port.lt.7)")),
            Map.entry(3, new CaseInputData(1, "name.neq.test\\)name")),
            Map.entry(4, new CaseInputData(1, "name.neq.test\\(name")),
            Map.entry(5, new CaseInputData(1, "(name.neq.test\\)name)")),
            Map.entry(6, new CaseInputData(1, "(name.neq.test\\(name)")),
            Map.entry(7, new CaseInputData(1, "name.neq.test\\(\\)name")),
            Map.entry(8, new CaseInputData(1, "name.neq.test\\)\\(name")),
            Map.entry(9, new CaseInputData(1, "name.neq.test\\~name")),
            Map.entry(10, new CaseInputData(1, "name.neq.test\\,name")),
            Map.entry(11, new CaseInputData(1, "name.neq.test*name")),
            Map.entry(12, new CaseInputData(1, "name.neq.test!name")),
            Map.entry(13, new CaseInputData(1, "name.like.testname*")),
            Map.entry(14, new CaseInputData(1, "name.nlike.testname!")),
            Map.entry(15, new CaseInputData(1, "name.like.test\\*name")),
            Map.entry(16, new CaseInputData(1, "name.nlike.test\\!name")),
            Map.entry(17, new CaseInputData(1, "port.between.0,1024")),
            Map.entry(18, new CaseInputData(1, "port.nbetween.0,1024")),
            Map.entry(19, new CaseInputData(1, "description.in.MyTest\\,yes,YourTest\\,ok.")),
            Map.entry(20, new CaseInputData(2, "(host.eq.stampede2.tacc.utexas.edu)~(default_access_method.in.PKI_KEYS,ACCESS_KEY)")),
            Map.entry(21, new CaseInputData(4, "(enabled.eq.true)~(owner.eq.jdoe)~(proxy_port.lt.7)~(system_type.in.OBJECT_STORE,LINUX)")),
            Map.entry(22, new CaseInputData(4, "(enabled.eq.true)~(port.lt.7)~(system_type.in.OBJECT_STORE,LINUX)~(description.like.my\\~system)")), // ~ in value
            Map.entry(23, new CaseInputData(4, "(enabled.eq.true)~(port.gte.7)~(description.like.my\\ system)~(system_type.in.OBJECT_STORE,LINUX)")), // space in value
            Map.entry(24, new CaseInputData(3, "(description.like.my\\,\\(\\)\\~\\*\\!\\\\system)~(port.lte.7)~(system_type.in.OBJECT_STORE)")), // 7 special chars in value: ,()~*!\
            Map.entry(25, new CaseInputData(2, "(description.like.my'\\\"system)~(port.lte.7)")), // more potentially problem chars ' "
            Map.entry(26, new CaseInputData(1, "description.like." + multiEscapeIn1)), // multiple escapes <1> <2> <2>
            Map.entry(27, new CaseInputData(1, "description.like." + multiEscapeIn2)), // multiple escapes <3> <1> <2> <4>
// TODO Figure out why this one fails. Looks like final escape at end of line is getting eaten when there are an odd number.
//    validCases.put(28,new CaseInputData(1, "description.like." + multiEscapeIn3)); // multiple escapes <1> <2> <3> <4> <5>
            Map.entry(29, new CaseInputData(0, "()~( )~()")),
            Map.entry(30, new CaseInputData(0, "()~()")),
            Map.entry(31, new CaseInputData(0, "~()~")),
            Map.entry(32, new CaseInputData(0, "()~")),
            Map.entry(33, new CaseInputData(0, "~()")),
            Map.entry(34, new CaseInputData(0, "()")),
            Map.entry(35, new CaseInputData(0, "(   )")),
            Map.entry(36, new CaseInputData(0, "~~~")),
            Map.entry(37, new CaseInputData(0, "~")),
            Map.entry(38, new CaseInputData(0, "")),
            Map.entry(39, new CaseInputData(0, null)));
  }
  private static final Map<Integer, CaseOutputData> validCaseOutputs;
  // Outputs
  // NOTE: For LIKE/NLIKE escaped special chars are retained.
  static
  {
    validCaseOutputs = Map.ofEntries(
    Map.entry( 1,new CaseOutputData(1, "enabled.EQ.true")),
    Map.entry( 2,new CaseOutputData(2, "port.LT.7")),
    Map.entry( 3,new CaseOutputData(3, "name.NEQ.test)name")),
    Map.entry( 4,new CaseOutputData(4, "name.NEQ.test(name")),
    Map.entry( 5,new CaseOutputData(5, "name.NEQ.test)name")),
    Map.entry( 6,new CaseOutputData(6, "name.NEQ.test(name")),
    Map.entry( 7,new CaseOutputData(7, "name.NEQ.test()name")),
    Map.entry( 8,new CaseOutputData(8, "name.NEQ.test)(name")),
    Map.entry( 9,new CaseOutputData(9, "name.NEQ.test~name")),
    Map.entry(10,new CaseOutputData(10, "name.NEQ.test,name")),
    Map.entry(11,new CaseOutputData(11, "name.NEQ.test*name")),
    Map.entry(12,new CaseOutputData(12, "name.NEQ.test!name")),
    Map.entry(13,new CaseOutputData(13, "name.LIKE.testname%")),
    Map.entry(14,new CaseOutputData(14, "name.NLIKE.testname_")),
    Map.entry(15,new CaseOutputData(15, "name.LIKE.test\\*name")),
    Map.entry(16,new CaseOutputData(16, "name.NLIKE.test\\!name")),
    Map.entry(17,new CaseOutputData(17, "port.BETWEEN.0,1024")),
    Map.entry(18,new CaseOutputData(18, "port.NBETWEEN.0,1024")),
    Map.entry(19,new CaseOutputData(19, "description.IN.MyTest\\,yes,YourTest\\,ok.")),
    Map.entry(20,new CaseOutputData(20, "host.EQ.stampede2.tacc.utexas.edu", "default_access_method.IN.PKI_KEYS,ACCESS_KEY")),
    Map.entry(21,new CaseOutputData(21, "enabled.EQ.true", "owner.EQ.jdoe", "proxy_port.LT.7", "system_type.IN.OBJECT_STORE,LINUX")),
    Map.entry(22,new CaseOutputData(22, "enabled.EQ.true", "port.LT.7", "system_type.IN.OBJECT_STORE,LINUX", "description.LIKE.my\\~system")),
    Map.entry(23,new CaseOutputData(23, "enabled.EQ.true", "port.GTE.7", "description.LIKE.my\\ system", "system_type.IN.OBJECT_STORE,LINUX")),
    Map.entry(24,new CaseOutputData(24, "description.LIKE.my\\,\\(\\)\\~\\*\\!\\\\system", "port.LTE.7", "system_type.IN.OBJECT_STORE")),
    Map.entry(25,new CaseOutputData(25, "description.LIKE.my'\\\"system", "port.LTE.7")),
    Map.entry(26,new CaseOutputData(26, "description.LIKE." + multiEscapeIn1)),
    Map.entry(27,new CaseOutputData(27, "description.LIKE." + multiEscapeIn2)),
    Map.entry(28,new CaseOutputData(28, "description.LIKE." + multiEscapeIn3)),
    Map.entry(29,new CaseOutputData(29, zeroResults)), // "()~( )~()",
    Map.entry(30,new CaseOutputData(30, zeroResults)), // "()~()",
    Map.entry(31,new CaseOutputData(31, zeroResults)), // "~()~",
    Map.entry(32,new CaseOutputData(32, zeroResults)), // "()~",
    Map.entry(33,new CaseOutputData(33, zeroResults)), // "~()",
    Map.entry(34,new CaseOutputData(34, zeroResults)), // "()",
    Map.entry(35,new CaseOutputData(35, zeroResults)), // "(  )"
    Map.entry(36,new CaseOutputData(36, zeroResults)), // "~~~",
    Map.entry(37,new CaseOutputData(37, zeroResults)), // "~",
    Map.entry(38,new CaseOutputData(38, zeroResults)), // ""
    Map.entry(39,new CaseOutputData(39, zeroResults))); // null
  }

  // Create input and validation data for invalid test cases
  private String[] invalidCaseInputs = new String[] {
          "(enabled.eq.true",
          "port.lt.7)",
          "name.neq.test)name",
          "name.neq.test(name",
          "(name.neq.test)name)",
          "(name.neq.test(name)",
          "(name.neq.test~name)",
          "(name.neq.test,name)",
          "enabled.eq.true~port.lt.7",
          "port.between.1",
          "port.between.1,2,3",
          "port.nbetween.1",
          "port.nbetween.1,2,3",
          "(host.eq.stampede2.tacc.utexas.edu)~default_access_method.in.PKI_KEYS,ACCESS_KEY",
          "(enabled.eq.true)~owner.eq.jdoe)~(proxy_port.lt.7)~(system_type.in.OBJECT_STORE,LINUX)",
          "(enabled.eq.true)~(~(system_type.in.OBJECT_STORE,LINUX)",
          "(enabled.eq.true)~)~(system_type.in.OBJECT_STORE,LINUX)",
          "(enabled.eq.tr)ue)~)~(system_type.in.OBJECT_STORE,LINUX)",
          ".eq.true",
          "true",
          "enabled.true",
          "1enabled.eq.true",
          "en$abled.eq.true",
          "(enabled.equal.true)",
          "(port.l@t.7)",
          "(host.eq.myhost)~(default_access_method.in.)",
          "(enabled.eq.true)~(proxy_port.lt.7)~(system_type.in)",
          "(enabled.eq.)~(proxy_port.lt.7)~(system_type.in.OBJECT_STORE,LINUX)",
          "(enabled.eq.true)~(proxy_port.lt.)~(system_type.in.OBJECT_STORE,LINUX)"
  };

  @BeforeSuite
  public void setUp()
  {

  }

  @AfterSuite
  public void tearDown()
  {
  }

  /*
   * Test validateAndExtractSearchList - valid cases
   */
  @Test(groups={"unit"})
  public void testValidateAndExtractSearchListValid()
  {
    // Make sure we have test data. When run using maven maps may be uninitialized if setup code not correct.
    Assert.assertTrue(validCaseInputs.size() > 0);
    // Iterate over test cases
    for (Map.Entry<Integer,CaseInputData> item : validCaseInputs.entrySet())
    {
      CaseInputData ci = item.getValue();
      int caseNum = item.getKey();
      System.out.println("Checking valid case # "+ caseNum + " Input: " + ci.searchListStr);
      // Extract the search list and validate that each condition has the correct form as done on the front end.
      List<String> validSearchList = SearchUtils.extractAndValidateSearchList(ci.searchListStr);
      var processedSearchList = new ArrayList<String>();
      // Validate and process each search condition as done on the back end.
      for (String condStr : validSearchList)
      {
        processedSearchList.add(SearchUtils.validateAndProcessSearchCondition(condStr));
      }
      // Validate result size
      System.out.println("  Result size: " + processedSearchList.size());
      assertEquals(validSearchList.size(), ci.count);
      // Validate result output
      for (int j = 0; j < ci.count; j++)
      {
        System.out.println("  Result string # " + j + " = " + processedSearchList.get(j));
        String coStr = validCaseOutputs.get(caseNum).strList.get(j);
        assertEquals(processedSearchList.get(j), coStr);
      }
    }
  }

  /*
   * Test validateAndExtractSearchList - invalid cases
   */
  @Test(groups={"unit"})
  public void testValidateAndExtractSearchListInvalid()
  {
    // Make sure we have test data. When run using maven maps may be uninitialized if setup code not correct.
    Assert.assertTrue(invalidCaseInputs.length > 0);
    // Iterate over test cases
    for (int i = 0; i < invalidCaseInputs.length; i++)
    {
      String searchListStr = invalidCaseInputs[i];
      System.out.println("Checking invalid case # "+ i + " Input: " + searchListStr);
      try
      {
        // Extract the search list and validate that each condition has the correct form as done on the front end.
        List<String> searchList = SearchUtils.extractAndValidateSearchList(searchListStr);
        // Validate and process each search condition as done on the back end.
        for (String condStr : searchList) {SearchUtils.validateAndProcessSearchCondition(condStr);}
        System.out.println("  Result size: " + searchList.size());
        fail("Expected IllegalArgumentException");
      }
      catch (IllegalArgumentException e)
      {
        System.out.println("Expected exception: " + e);
      }
    }
    // TODO: Explicitly check for certain exceptions rather than just that an IllegalArg has been thrown.
    //       E.g., (port.l@t.7) should throw an exception containing SEARCH_COND_INVALID_OP
  }

  /*
   * Test buildListFromQueryParms - valid cases
   */
  @Test(groups={"unit"})
  public void testBuildListFromQueryParmsValid()
  {
    // Make sure we have test data. When run using maven maps may be uninitialized if setup code not correct.
    Assert.assertTrue(validCaseInputs.size() > 0);
    // Iterate over test cases
    for (Map.Entry<Integer,CaseInputData> item : validCaseInputs.entrySet())
    {
      CaseInputData ci = item.getValue();
      int caseNum = item.getKey();
      // If it is a test case with no expected output then skip it
      if (validCaseOutputs.get(caseNum).strList.get(0) == null) continue;
      System.out.println("Checking case # "+ caseNum + " Input: " + ci.searchListStr);

      // Use CaseInput data to construct a list of query parameters based on the form attr.op=value
      // Use utility method to create the initial list of search conditions
      List<String> searchConditions = SearchUtils.extractAndValidateSearchList(ci.searchListStr);

      // For each search condition convert it to a query parameter
      var queryParms = new MultivaluedHashMap<String, String>();
      for (String condStr : searchConditions)
      {
        String attr = SearchUtils.extractAttribute(condStr);
        SearchUtils.SearchOperator op = SearchUtils.extractOperator(condStr);
        String qParmKey = attr + "." + op.name();
        String fullValueStr = SearchUtils.extractFullValueStr(condStr, op);
        queryParms.addAll(qParmKey, Collections.singletonList(fullValueStr));
      }

      // Extract the search list and validate that each condition has the correct form as done on the front end.
      List<String> validSearchList = SearchUtils.buildListFromQueryParms(queryParms);
      var processedSearchList = new ArrayList<String>();
      // Validate and process each search condition as done on the back end.
      for (String condStr : validSearchList)
      {
        processedSearchList.add(SearchUtils.validateAndProcessSearchCondition(condStr));
      }
      // Validate result size
      System.out.println("  Result size: " + processedSearchList.size());
      assertEquals(validSearchList.size(), ci.count);
      // Validate result output. Results come back unordered so make sure each condition
      // is somewhere in the list of expected outputs
      for (int j = 0; j < processedSearchList.size(); j++)
      {
        System.out.println("  Result string # " + j + " = " + processedSearchList.get(j));
        checkQParmResult(processedSearchList.get(j), validCaseOutputs.get(caseNum).strList);
      }
    }
  }

  /*
   * Test isTimestamp - valid cases
   */
  @Test(groups={"unit"})
  public void testIsTimestamp()
  {
    // Make sure we have test data. When run using maven maps may be uninitialized if setup code not correct.
    Assert.assertTrue(validTimestamps.length > 0);
    // Iterate over valid test cases
    for (int i = 0; i < validTimestamps.length; i++)
    {
      String timestampStr = validTimestamps[i];
      System.out.println("Checking valid case # "+ i + " Input: " + timestampStr);
      Assert.assertTrue(SearchUtils.isTimestamp(timestampStr), "Input timestamp string: " + timestampStr);
    }
    // Iterate over invalid test cases
    for (int i = 0; i < invalidTimestamps.length; i++)
    {
      String timestampStr = invalidTimestamps[i];
      System.out.println("Checking invalid case # "+ i + " Input: " + timestampStr);
      Assert.assertFalse(SearchUtils.isTimestamp(timestampStr), "Input timestamp string: " + timestampStr);
    }
  }

  /*
   * Test convertValuesToTimestamps - valid cases
   */
  @Test(groups={"unit"})
  public void testConvertValuesToTimestamps()
  {
    // Make sure we have test data. When run using maven maps may be uninitialized if setup code not correct.
    Assert.assertTrue(validTimestamps.length > 0);
    // Test by iterating over valid test cases and incrementally building a list of values.
    // Check call after each increment
    SearchUtils.SearchOperator op = SearchUtils.SearchOperator.IN;
    StringJoiner sj = new StringJoiner(",");
    for (int i = 0; i < validTimestamps.length; i++)
    {
      String timestampStr = validTimestamps[i];
      System.out.println("Checking valid case # "+ i + " Input: " + timestampStr);
      sj.add(timestampStr);
      String testList = sj.toString();
      String testResult = SearchUtils.convertValuesToTimestamps(op, testList);
      System.out.println("   Test result: " + testResult);
      Assert.assertNotNull(testResult);
    }
  }

  /*
   * Test camelCaseToSnakeCase
   */
  @Test(groups={"unit"})
  public void testCamelCaseToSnakeCase()
  {
    assertEquals(SearchUtils.camelCaseToSnakeCase("a"), "a");
    assertEquals(SearchUtils.camelCaseToSnakeCase("A"), "a");
    assertEquals(SearchUtils.camelCaseToSnakeCase("ThisColumn"), "this_column");
    assertEquals(SearchUtils.camelCaseToSnakeCase("thisColumn"), "this_column");
    assertEquals(SearchUtils.camelCaseToSnakeCase("ThisIsAColumn"), "this_is_a_column");
    assertEquals(SearchUtils.camelCaseToSnakeCase("systemType"), "system_type");
    assertEquals(SearchUtils.camelCaseToSnakeCase("id"), "id");
    assertEquals(SearchUtils.camelCaseToSnakeCase("effectiveUserId"), "effective_user_id");
    assertEquals(SearchUtils.camelCaseToSnakeCase("jobRemoteArchiveSystem"), "job_remote_archive_system");
  }

  // ************************************************************************
  // **************************  Private Methods  ***************************
  // ************************************************************************

  private static void checkQParmResult(String resultCond, List<String> validOutputs)
  {
    for (String validStr : validOutputs)
    {
      if (resultCond.equals(validStr)) return;
    }
    Assert.fail("Result not found in expected result. Result = " + resultCond);
  }


  // ************************************************************************
  // **************************  Classes  ***********************************
  // ************************************************************************

  // Case input data consists of the case number, result count and an input string
  static class CaseInputData
  {
    public final int count;
    public final String searchListStr;
    CaseInputData(int r, String s) { count=r; searchListStr=s; }
  }

  // Case output data consists of the case number and an array of strings
  static class CaseOutputData
  {
    public final int caseNum;
    public final List<String> strList;
    CaseOutputData(int c, String... parms)
    {
      caseNum = c;
      strList = new ArrayList<>();
      if (parms != null) strList.addAll(Arrays.asList(parms));
    }
  }
}
