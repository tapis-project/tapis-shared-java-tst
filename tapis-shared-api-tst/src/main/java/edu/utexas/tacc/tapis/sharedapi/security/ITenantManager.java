package edu.utexas.tacc.tapis.sharedapi.security;

import java.time.Instant;
import java.util.Map;

import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.exceptions.runtime.TapisRuntimeException;
import edu.utexas.tacc.tapis.tenants.client.gen.model.Tenant;

public interface ITenantManager {

    /* ---------------------------------------------------------------------------- */
    /* getTenants:                                                                  */
    /* ---------------------------------------------------------------------------- */
    /** Return the map of tenant ids to tenant objects of all known tenants. This 
     * method is typically used by services to force the initialization of the tenants 
     * map.  If the map hasn't been retrieved from the tenants service, it will be 
     * downloaded.  Otherwise, the previously downloaded map will be returned. 
     * 
     * @return the tenants map
     * @throws TapisRuntimeException if the list cannot be attained
     */
    Map<String, Tenant> getTenants() throws TapisRuntimeException;

    /* ---------------------------------------------------------------------------- */
    /* refreshTenants:                                                              */
    /* ---------------------------------------------------------------------------- */
    /** This method forces a refresh of the tenants map as long as the minimum 
     * update interval has been exceeded. The map is from tenant ids to tenant objects.
     * Clients typically don't need to call this method as it will be automatically 
     * called if a tenant is not found.
     * 
     * When an event bus is integrated into Tapis, this method can be replaced by
     * event triggered refreshes.
     * 
     * @return a tenants list that may have been refreshed
     * @throws TapisRuntimeException if a map cannot be attained
     */
    Map<String, Tenant> refreshTenants() throws TapisRuntimeException;

    /* ---------------------------------------------------------------------------- */
    /* getTenant:                                                                   */
    /* ---------------------------------------------------------------------------- */
    /** Get a tenant definition from the cached list.  If the tenant is not found in
     * the list, or if the list has not been initialized, an attempt to retrieve the
     * current list will be made if the minimum refresh interval has expired.
     * 
     * @param tenantId the id of the tenant 
     * @return the non-null tenant
     * @throws TapisException if no tenant can be returned
     */
    Tenant getTenant(String tenantId) throws TapisException;

    /* ---------------------------------------------------------------------------- */
    /* allowTenantId:                                                               */
    /* ---------------------------------------------------------------------------- */
    /** Is the tenant specified in the JWT, jwtTenantId, allowed to specify the 
     * hdrTenantId in the X-Tapis-Tenant header?  This method calculates whether a
     * service or user in one tenant can make a request on behalf of a servie or user 
     * in another tenant. 
     * 
     * If the number of allowable tenants become large (in the 100s), it may be
     * desirable to cache a hash of the list to improve look up time. 
     * 
     * @param jwtTenantId the tenant contained in a JWT's tapis/tenant_id claim
     * @param hdrTenantId the tenant on behalf of whom a request is being made
     * @return true if the tenant substitution is allowed, false otherwise
     * @throws TapisException if the jwt tenant object cannot be retrieved 
     */
    boolean allowTenantId(String jwtTenantId, String hdrTenantId) throws TapisException;

    /* ---------------------------------------------------------------------------- */
    /* getTenantServiceBaseUrl:                                                     */
    /* ---------------------------------------------------------------------------- */
    String getTenantServiceBaseUrl();

    /* ---------------------------------------------------------------------------- */
    /* getLastUpdateTime:                                                           */
    /* ---------------------------------------------------------------------------- */
    Instant getLastUpdateTime();
}