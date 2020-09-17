package edu.utexas.tacc.tapis.sharedapi.security;

import java.security.Principal;

/**
 *
 */
public class AuthenticatedUser implements Principal {

    private final String jwtUser;
    private final String jwtTenantId;
    private final String accountType;
    private final String delegator;
    private final String oboUser;
    private final String oboTenantId;
    private final String headerUserTokenHash;
    private final String jwt;

    public AuthenticatedUser(String jwtUser, String jwtTenantId, String accountType, String delegator,
                             String oboUser, String oboTenantId, String headerUserTokenHash, 
                             String jwt) 
    {
        this.jwtUser = jwtUser;
        this.jwtTenantId = jwtTenantId;
        this.accountType = accountType;
        this.delegator = delegator;
        this.oboUser = oboUser;
        this.oboTenantId = oboTenantId;
        this.headerUserTokenHash = headerUserTokenHash;
        this.jwt = jwt;
    }

    @Override
    public String getName() {
        return jwtUser;
    }

    public String getAccountType() { return accountType; }

    public String getDelegator() { return delegator; }

    public String getTenantId() {
        return jwtTenantId;
    }

    public String getJwt() {
        return jwt;
    }

    public String getOboUser() {
        return oboUser;
    }

    public String getOboTenantId() {
        return oboTenantId;
    }

    public String getHeaderUserTokenHash() {
        return headerUserTokenHash;
    }

}

