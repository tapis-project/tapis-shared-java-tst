package edu.utexas.tacc.tapis.shared.threadlocal;

import org.apache.commons.lang3.StringUtils;

public final class TapisThreadContext 
 implements Cloneable
{
	/* **************************************************************************** */
	/*                                   Constants                                  */
	/* **************************************************************************** */
    // An invalid tenant id string that indicates an uninitialized tenant id.
	public static final String INVALID_ID = "?";
	
    /* **************************************************************************** */
    /*                                     Enums                                    */
    /* **************************************************************************** */
	// The account type determines the token type.
	public enum AccountType {user, service}
	
	/* **************************************************************************** */
	/*                                    Fields                                    */
	/* **************************************************************************** */
	// The tenant and user of the current thread's request initialized to non-null.
	private String jwtTenantId = INVALID_ID;    // as specified in the JWT
	private String jwtUser = INVALID_ID;        // as specified in the JWT
    private String oboTenantId = INVALID_ID;    // on-behalf-of tenant id
    private String oboUser = INVALID_ID;        // on-behalf-of user

    // Information that is extracted from query parameters
    // TODO Re-evaluate - remove if not ultimately used
    private boolean prettyPrint = false;
//    private List<String> searchList;
//    private int limit = -1;
//    private String startAfter;

    // Account type also cannot be null.  The delegator subject is either null when
    // no delegation has occurred or in the 'user@tenant' format when there is 
    // delegation.
	private AccountType accountType;            // determines source of user value
    private String delegatorSubject = null;     // always from JWT
    
    // Information from service type JWTs
    private String UserJwtHash;                 // X-Tapis-User-Token-Hash header
	
	// The execution context is set at a certain point in request processing, 
	// usually well after processing has begun.
	private TapisExecutionContext executionContext = null;
	
    /* **************************************************************************** */
    /*                                Public Methods                                */
    /* **************************************************************************** */
	@Override
	public TapisThreadContext clone() throws CloneNotSupportedException 
	{
	    return (TapisThreadContext) super.clone();
	}
	
	/** Validate the generic parameters required for most request processing.  This
	 * does not include execution context validation that are used only in some requests.
	 * 
	 * @return true if parameters are valid, false otherwise.
	 */
	public boolean validate()
	{
	    // Make sure required parameters have been assigned.
	    if (INVALID_ID.contentEquals(jwtTenantId) || StringUtils.isBlank(jwtTenantId)) return false;
	    if (INVALID_ID.contentEquals(jwtUser)     || StringUtils.isBlank(jwtUser))     return false;
	    if (accountType == null) return false;
	            
        // These are required on service tokens.
        if (accountType == AccountType.service) {
            if (INVALID_ID.contentEquals(oboTenantId) || StringUtils.isBlank(oboTenantId)) return false;
            if (INVALID_ID.contentEquals(oboUser)     || StringUtils.isBlank(oboUser))     return false;
        }

	    return true;
	}

    /** Validate that the execution context has been set.
     * 
     * @return true if parameters are valid, false otherwise.
     */
    public boolean validateExecutionContext(){return getExecutionContext() != null;}

	/* **************************************************************************** */
	/*                                   Accessors                                  */
	/* **************************************************************************** */
//    public String getStartAfter(){return startAfter;}
//    public void setStartAfter(String s) { startAfter = s; }
//    public int getLimit(){return limit;}
//    public void setLimit(int i) { limit = i; }
//    public List<String> getSearchList(){return searchList;}
//    public void setSearchList(List<String> l) { searchList = l; }
    public boolean getPrettyPrint(){return prettyPrint;}
    public void setPrettyPrint(boolean p) { prettyPrint = p; }
	public String getJwtTenantId(){return jwtTenantId;}
	public void setJwtTenantId(String tenantId) {
		if (!StringUtils.isBlank(tenantId)) this.jwtTenantId = tenantId;
	}
	
	public String getJwtUser(){return jwtUser;}
	public void setJwtUser(String user) {
	    if (!StringUtils.isBlank(user)) this.jwtUser = user;
	}

    public String getOboTenantId(){return oboTenantId;}
    public void setOboTenantId(String oboTenantId) {
        if (!StringUtils.isBlank(oboTenantId)) this.oboTenantId = oboTenantId;
    }

    public String getOboUser(){return oboUser;}
    public void setOboUser(String oboUser) {
        if (!StringUtils.isBlank(oboUser)) this.oboUser = oboUser;
    }

    public AccountType getAccountType() {return accountType;}
    public void setAccountType(AccountType accountType) {this.accountType = accountType;}
	
    public TapisExecutionContext getExecutionContext() {return executionContext;}
    public void setExecutionContext(TapisExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public String getDelegatorSubject() {
        return delegatorSubject;
    }
    public void setDelegatorSubject(String delegatorSubject) {
        this.delegatorSubject = delegatorSubject;
    }

    public String getUserJwtHash() {
        return UserJwtHash;
    }

    public void setUserJwtHash(String userJwtHash) {
        UserJwtHash = userJwtHash;
    }
}
