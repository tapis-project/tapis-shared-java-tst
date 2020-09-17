package edu.utexas.tacc.tapis.sharedapi.security;

import java.time.Instant;

import edu.utexas.tacc.tapis.tokens.client.model.TokenResponsePackage;

public interface IServiceJWT 
{
    // Original inputs.
    String getServiceName();

    String getTenant();

    String getTokensBaseUrl();

    TokenResponsePackage getTokPkg();

    int getAccessTTL();

    int getRefreshTTL();

    String getDelegationTenant();

    String getDelegationUser();

    String getAdditionalClaims();

    String getAccessJWT();

    Instant getAccessExpiresAt();

    Integer getAccessExpiresIn();

    boolean hasExpiredAccessJWT();

    int getRefreshCount();

    Instant getLastRefreshTime();
}