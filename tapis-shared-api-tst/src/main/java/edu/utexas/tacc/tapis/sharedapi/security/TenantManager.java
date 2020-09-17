package edu.utexas.tacc.tapis.sharedapi.security;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.exceptions.runtime.TapisRuntimeException;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shared.utils.CallSiteToggle;
import edu.utexas.tacc.tapis.tenants.client.TenantsClient;
import edu.utexas.tacc.tapis.tenants.client.gen.model.Tenant;

public class TenantManager
 implements ITenantManager 
{
    /* **************************************************************************** */
    /*                                   Constants                                  */
    /* **************************************************************************** */
    // Local logger.
    private static final Logger _log = LoggerFactory.getLogger(TenantManager.class);
    
    // Tenants service path.
    private static final String TENANTS_PATH = "v3/tenants";
    
    // Minimum time allowed between refreshes.
    private static final long MIN_REFRESH_SECONDS = 600; // 10 minutes

    /* **************************************************************************** */
    /*                                    Fields                                    */
    /* **************************************************************************** */
    // Singleton instance.
    private static TenantManager _instance;
    
    // Base url for the tenant's service.
    private final String          _tenantServiceBaseUrl;
    
    // The map tenant ids to tenants retrieved from the tenant's service.
    private Map<String,Tenant>    _tenants;
    
    // Time of the last update.
    private Instant               _lastUpdateTime;
    
    // Toggle switch that limits log output.
    private static final CallSiteToggle _lastGetTenantsSucceeded = new CallSiteToggle();
    
    /* **************************************************************************** */
    /*                                 Constructors                                 */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* constructor:                                                                 */
    /* ---------------------------------------------------------------------------- */
    private TenantManager(String tenantServiceBaseUrl)
    {
        // Make sure the url ends with a slash.
        if (!tenantServiceBaseUrl.endsWith("/")) tenantServiceBaseUrl += "/";
        _tenantServiceBaseUrl = tenantServiceBaseUrl;
    }
    
    /* **************************************************************************** */
    /*                                Public Methods                                */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* getInstance:                                                                 */
    /* ---------------------------------------------------------------------------- */
    public static TenantManager getInstance(String tenantServiceBaseUrl)
     throws TapisRuntimeException
    {
        // Only create a new instance if one doesn't exist.
        if (_instance == null) {
            
            // The base url to the tenants service must be provided.
            if (StringUtils.isBlank(tenantServiceBaseUrl)) {
                String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "getInstance", 
                                             "tenantServiceBaseUrl");
                _log.error(msg);
                throw new TapisRuntimeException(msg);
            }
            
            // Enter the monitor and check instance again before taking action.
            synchronized (TenantManager.class) {
                if (_instance == null)
                    _instance = new TenantManager(tenantServiceBaseUrl);
            }
        }
        return _instance;
    }

    /* ---------------------------------------------------------------------------- */
    /* getInstance:                                                                 */
    /* ---------------------------------------------------------------------------- */
    /** Only use this if you KNOW the singleton instance has been initialized.
     * 
     * @return the non-null instance
     * @throws TapisRuntimeException if the tenants manager instance does not exist
     */
    public static TenantManager getInstance() throws TapisRuntimeException
    {
        // Throw runtime exception if we are not initialized.
        if (_instance == null) {
            String msg = MsgUtils.getMsg("TAPIS_TENANT_MGR_NOT_INITIALIZED");
            _log.error(msg);
            throw new TapisRuntimeException(msg);
        }
        return _instance;
    }
    
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
    @Override
    public Map<String,Tenant> getTenants() throws TapisRuntimeException
    {
        // Initialize the list if necessary.
        if (_tenants == null) {
            synchronized (TenantManager.class) {
                // Avoid race condition.
                if (_tenants == null) {
                    try {
                        // Get the tenant list from the tenant service.
                        var client = new TenantsClient(_tenantServiceBaseUrl);
                        var tenantList = client.getTenants();
                        
                        // Create the hashmap.
                        _tenants = new LinkedHashMap<String,Tenant>(1+tenantList.size()*2);
                        for (Tenant t : tenantList) _tenants.put(t.getTenantId(), t); 
                    } catch (Exception e) {
                        String msg = MsgUtils.getMsg("TAPIS_TENANT_LIST_ERROR",
                                                     getTenantsPath());
                        if (_lastGetTenantsSucceeded.toggleOff()) _log.error(msg, e);
                        throw new TapisRuntimeException(msg, e);
                    }
                    
                    // Mark the time of the download.
                    _lastUpdateTime = Instant.now();
                    _lastGetTenantsSucceeded.toggleOn();
                    
                    // Write a message to the log.
                    if (_log.isInfoEnabled())
                        _log.info(MsgUtils.getMsg("TAPIS_TENANT_LIST_RECIEVED",
                                                  getTenantsPath()));
                }
            }
        }
        
        return _tenants;
    }
    
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
    @Override
    public Map<String,Tenant> refreshTenants() throws TapisRuntimeException
    {
        // Maybe we are not initialized.
        if (_tenants == null) return getTenants();
        
        // Guard against denial of service attacks.
        if (!allowRefresh()) return getTenants();
        
        // Clear and repopulate the stale list.
        synchronized (TenantManager.class) {
            _tenants = null;
            return getTenants();
        }
    }
    
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
    @Override
    public Tenant getTenant(String tenantId) throws TapisException
    {
        // See if we can find the tenant.
        var tenants = getTenants();
        Tenant t = tenants.get(tenantId);
        if (t != null) return t;
        
        // The tenant was not found, maybe a refresh will help.
        tenants = refreshTenants();
        t = tenants.get(tenantId);
        if (t != null) return t;
        
        // Throw an exception if we can't find the tenant.
        String msg = MsgUtils.getMsg("TAPIS_TENANT_LIST_ERROR",
                                     getTenantsPath());
        _log.error(msg);
        throw new TapisException(msg);
    }
    
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
    @Override
    public boolean allowTenantId(String jwtTenantId, String hdrTenantId)
     throws TapisException
    {
        var tenant = getTenant(jwtTenantId);
        List<String> allowable = tenant.getAllowableXTenantIds();
        if (allowable != null && allowable.contains(hdrTenantId)) return true;
          else return false;
    }
    
    /* ---------------------------------------------------------------------------- */
    /* getTenantServiceBaseUrl:                                                     */
    /* ---------------------------------------------------------------------------- */
    @Override
    public String getTenantServiceBaseUrl() {return _tenantServiceBaseUrl;}

    /* ---------------------------------------------------------------------------- */
    /* getLastUpdateTime:                                                           */
    /* ---------------------------------------------------------------------------- */
    @Override
    public Instant getLastUpdateTime() {return _lastUpdateTime;}

    /* **************************************************************************** */
    /*                               Private Methods                                */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* getTenantsPath:                                                              */
    /* ---------------------------------------------------------------------------- */
    private String getTenantsPath() {return _tenantServiceBaseUrl + TENANTS_PATH;}
    
    /* ---------------------------------------------------------------------------- */
    /* allowRefresh:                                                                */
    /* ---------------------------------------------------------------------------- */
    /** Has the minimum time between tenant list refreshes expired?
     * 
     * @return true if refresh is allowed, false otherwise
     */
    private boolean allowRefresh()
    {
        // Don't allow too many refreshes in a row.
        if (_lastUpdateTime == null) return true;
        if (Instant.now().isAfter(_lastUpdateTime.plusSeconds(MIN_REFRESH_SECONDS)))
            return true;
        return false;
    }
}
