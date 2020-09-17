package edu.utexas.tacc.tapis.sharedapi.security;

import java.util.Map;

public final class ServiceJWTParms 
{
    // Default time-to-live values for each token type.
    public static final int DEFAULT_ACCESS_TTL_SECS = 14400; // 4 hours
    public static final int DEFAULT_REFRESH_TTL_SECS = DEFAULT_ACCESS_TTL_SECS + 60; // + 1 minute
    
    // Client parameters.
    private String serviceName;
    private String tenant;
    private String tokensBaseUrl;
    private int    accessTTL = DEFAULT_ACCESS_TTL_SECS;
    private int    refreshTTL = DEFAULT_REFRESH_TTL_SECS;
    private String delegationSubjectTenant;
    private String delegationSubjectUser;
    private Map<String,Object> additionalClaims;
    
    // Accessors.
    public String getServiceName() {
        return serviceName;
    }
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    public String getTenant() {
        return tenant;
    }
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    public String getTokensBaseUrl() {
        return tokensBaseUrl;
    }
    public void setTokensBaseUrl(String tokensBaseUrl) {
        this.tokensBaseUrl = tokensBaseUrl;
    }
    public int getAccessTTL() {
        return accessTTL;
    }
    public void setAccessTTL(int accessTTL) {
        this.accessTTL = accessTTL;
    }
    public int getRefreshTTL() {
        return refreshTTL;
    }
    public void setRefreshTTL(int refreshTTL) {
        this.refreshTTL = refreshTTL;
    }
    public String getDelegationSubjectTenant() {
        return delegationSubjectTenant;
    }
    public void setDelegationSubjectTenant(String delegationSubjectTenant) {
        this.delegationSubjectTenant = delegationSubjectTenant;
    }
    public String getDelegationSubjectUser() {
        return delegationSubjectUser;
    }
    public void setDelegationSubjectUser(String delegationSubjectUser) {
        this.delegationSubjectUser = delegationSubjectUser;
    }
    public Map<String, Object> getAdditionalClaims() {
        return additionalClaims;
    }
    public void setAdditionalClaims(Map<String, Object> additionalClaims) {
        this.additionalClaims = additionalClaims;
    }
}
