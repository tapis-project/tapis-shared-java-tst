package edu.utexas.tacc.tapis.search;

import javax.jms.InvalidSelectorException;

import org.apache.activemq.filter.BooleanExpression;
import org.testng.annotations.Test;

/** Basic tests of the generated parser.
 * 
 * @author rcardone
 */
@Test(groups= {"unit"})
public class ParserTest 
{
    /* ********************************************************************** */
    /*                              Test Methods                              */
    /* ********************************************************************** */    
    /* ---------------------------------------------------------------------- */
    /* goodFilters:                                                           */
    /* ---------------------------------------------------------------------- */
    @Test(enabled=true)
    public void goodFilters() throws InvalidSelectorException
    {
        // Test a simple conjunction.
        String filter = "name = '" + "Bud" + "' AND tenant_id = '" + "iplantc.org" + "'";
        var ast = TapisSelectorParser.parse(filter);
        //  System.out.println(ast);
        
        // Test more complex expressions.
        filter = "int1 > 66 AND int2 <> 5 AND (name LIKE 'Jo%n' OR range BETWEEN 200 AND 300)";
        ast = TapisSelectorParser.parse(filter);
        
        // Test datetime ranges.  Dates can only be handled using epoch time.
        long millis = System.currentTimeMillis();
        filter = "date BETWEEN " + (millis - 10000) + " AND " + (millis - 1000);
        ast = TapisSelectorParser.parse(filter);

        // Test NOT LIKE filter.
        filter = "name NOT LIKE 'Bi__y'";
        ast = TapisSelectorParser.parse(filter);
        
        // Test escape characters.
        // Note that any character can be used to escape _ and % in LIKE clauses.
        filter = "name LIKE 'George\\_%' ESCAPE '\\'";
        ast = TapisSelectorParser.parse(filter);
        
        // Test IN.
        filter = "country IN ('UK', 'US')";
        ast = TapisSelectorParser.parse(filter);
        
        // Test NULL
        filter = "missing is NULL";
        ast = TapisSelectorParser.parse(filter);
    }

    /* ---------------------------------------------------------------------- */
    /* badFilters:                                                            */
    /* ---------------------------------------------------------------------- */
    /** Each of these expressions are expected to be invalid and throw a parser
     * exception.  If no exception is thrown, this test fails.
     * 
     */
    @Test(enabled=true)
    public void badFilters()
    {
        // Test a simple conjunction.
        boolean exceptionOccurred = false;
        String filter = "name = '" + "Bud" + "' tenant_id = '" + "iplantc.org" + "'";
        BooleanExpression ast;
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);
        
        // Test more complex expressions.
        exceptionOccurred = false;
        filter = "int1 > AND int2 <> 5 AND (name LIKE 'Jo%n' OR range BETWEEN 200 AND 300)";
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);
        
        // Test datetime ranges.  Dates can only be handled using epoch time.
        exceptionOccurred = false;
        long millis = System.currentTimeMillis();
        filter = "date BETWEEN " + (millis - 10000) + " AND ";
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);

        // Test NOT LIKE filter.
        exceptionOccurred = false;
        filter = "name NOT LIKE '";
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);
        
        // Test escape characters.
        // Note that any character can be used to escape _ and % in LIKE clauses.
        exceptionOccurred = false;
        filter = "LIKE 'George\\_%' ESCAPE '\\'";
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);
        
        // Test IN.
        exceptionOccurred = false;
        filter = "country IN ('UK', 'US'";
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);
        
        // Test REGEX as an indication that all function calls have been removed
        // from the language.  If we want to add function calls in the future, we
        // can selective remove the built-in function by deregistering them:
        // --> FunctionCallExpression.deregisterFunction("REGEX");
        exceptionOccurred = false;
        filter = "REGEX('^a.c', 'abc')"; // hardcoded regex and value example
        try {ast = TapisSelectorParser.parse(filter);}
            catch (InvalidSelectorException e) {exceptionOccurred = true;}
        if (!exceptionOccurred) throw new IllegalArgumentException(filter);
  }
}
