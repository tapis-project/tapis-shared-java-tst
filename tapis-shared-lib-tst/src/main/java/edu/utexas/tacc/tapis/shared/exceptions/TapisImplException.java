package edu.utexas.tacc.tapis.shared.exceptions;

public final class TapisImplException
 extends TapisException
{
    private static final long serialVersionUID = -8163399520088986524L;
    
    // Condition codes allow backend code to communicate the type of the 
    // error to the frontend might reflect.  The frontend has complete
    // discretion on what it reports to the user, but the intention is that
    // condition codes provide a 1-to-1 mapping to frontend error codes.
    //
    // Specifically, the condition text should exactly match http status 
    // definitions, such as those defined in javax.ws.rs.core.Response.Status 
    // so that the front end code can directly translate condition enums
    // in to status codes.
    //
    // Also see TapisNotFoundException for missing data errors.
    public enum Condition {
        INTERNAL_SERVER_ERROR,
        BAD_REQUEST,
        NOT_FOUND
    }
    
    // The condition code should always be set. 
    public final Condition condition;
    
    // Chosen condition codes.
    public TapisImplException(String message, Condition cond) 
    {super(message); condition = cond;}
    public TapisImplException(String message, Throwable cause, Condition cond) 
    {super(message, cause); condition = cond;}

    // Condition codes calculated from http status code. 
    public TapisImplException(String message, int status) 
    {super(message); condition = translate(status);}
    public TapisImplException(String message, Throwable cause, int status) 
    {super(message, cause); condition = translate(status);}

    /** Translate an http error code into a condition code. This method
     * should not be called with status codes less than 400.
     * 
     * @param status an http status code >= 400
     * @return the condition code to present externally
     */
    private Condition translate(int status)
    {
        if (status == 404) return Condition.NOT_FOUND;
        if (status >= 400 && status < 500) return Condition.BAD_REQUEST;
        return Condition.INTERNAL_SERVER_ERROR;
    }
}
