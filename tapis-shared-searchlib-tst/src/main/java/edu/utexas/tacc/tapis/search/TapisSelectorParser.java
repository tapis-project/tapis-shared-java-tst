package edu.utexas.tacc.tapis.search;

import javax.jms.InvalidSelectorException;

import org.apache.activemq.filter.BooleanExpression;
import org.apache.activemq.selector.SelectorParser;

/** Wrapper for generated class.
 * 
 * @author rcardone
 */
public class TapisSelectorParser 
{
    // Get the AST representing the expression when it successfully parses.
    // An exception is thrown when the exception is not accepted.
    public static BooleanExpression parse(String sql) throws InvalidSelectorException
    {
        return SelectorParser.parse(sql);
    }
}
