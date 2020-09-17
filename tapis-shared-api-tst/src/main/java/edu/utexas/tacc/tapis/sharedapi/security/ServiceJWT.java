package edu.utexas.tacc.tapis.sharedapi.security;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import edu.utexas.tacc.tapis.client.shared.exceptions.TapisClientException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shared.utils.TapisGsonUtils;
import edu.utexas.tacc.tapis.tokens.client.TokensClient;
import edu.utexas.tacc.tapis.tokens.client.gen.model.InlineObject1.AccountTypeEnum;
import edu.utexas.tacc.tapis.tokens.client.model.CreateTokenParms;
import edu.utexas.tacc.tapis.tokens.client.model.RefreshTokenParms;
import edu.utexas.tacc.tapis.tokens.client.model.TokenResponsePackage;

/** This class fetches a single service token and will refresh that token indefinitely.
 * Services use the getAccessJWT() method to get the currently valid access JWT in 
 * serialized form.  They can also call hasExpiredAccessToken() to determine whether
 * this class instance should be discarded because the access token can no longer be
 * used.  
 * 
 * The parameters passed to this class on construction cannot be changed, but any 
 * number of instances can be created for the same service.  The service password is 
 * used only to acquire a new token from the Tokens service during construction and 
 * is not saved in this class's instances.  If the constructor returns without 
 * throwing an exception, then a newly minted access token has been received from
 * the Tokens service and is ready for use.
 * 
 * The default access and refresh token lifetimes are set in the ServiceJWTParms
 * class by default, but can be overridden.  The refresh time-to-live must be at least
 * as long as the access token's, but making it much longer is of no use since no 
 * attempt to refresh the access token occur after the access token expires. 
 * 
 * @author rcardone
 */
public class ServiceJWT
 implements Thread.UncaughtExceptionHandler, IServiceJWT
{
    /* **************************************************************************** */
    /*                                   Constants                                  */
    /* **************************************************************************** */
    // Local logger.
    private static final Logger _log = LoggerFactory.getLogger(ServiceJWT.class);
    
    // Refresh thread information.
    private static final String THREADGROUP_NAME    = "JwtTokRefreshGroup";
    private static final String REFRESH_THREAD_NAME = "JwtTokRefreshThread";
    
    // Milliseconds before expiration time to initiate the first refresh operation.
    private static final long   REFRESH_TIME_MILLIS    = 30 * 60 * 1000; // 30 minutes
    private static final double REFRESH_SHORT_FRACTION = .8;
    
    // Refresh retry times.
    private static final long   DFT_REFRESH_RETRY_MILLIS   = 5 * 60 * 1000; // 5 minutes
    private static final long   MIN_REFRESH_MILLIS         = 8000; // 8 seconds
    
    /* **************************************************************************** */
    /*                                    Fields                                    */
    /* **************************************************************************** */
    // Constructor input.
    private final String _serviceName;
    private final String _tenant;
    private final String _tokensBaseUrl;
    private final int    _accessTTL;
    private final int    _refreshTTL;
    private final String _delegationTenant;
    private final String _delegationUser;
    private final String _additionalClaims;
    
    // Generated tokens. We use unsynchronized accessors to read contents since
    // once the field is assign all thread only read its values.  Upon refresh,
    // the JVM guarantees that the field reference is atomically assigned.
    private volatile TokenResponsePackage _tokPkg;
    
    // Fields that get updated on each successful refresh.
    private int     _refreshCount;
    private Instant _lastRefreshTime;
    
    // The thread in charge of automatic refresh operations.
    private TokenRefreshThread _refreshThread;
    
    /* **************************************************************************** */
    /*                                 Constructors                                 */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* constructor:                                                                 */
    /* ---------------------------------------------------------------------------- */
    /** To limit the attack surface, we don't save the service password that gets passed
     * to the Tokens service.  Instead we use it here to get the initial tokens and
     * then discard it. 
     * 
     * @param parms parameters used to create access and refresh tokens
     * @param servicePassword the service password passed to the Tokens service
     * @throws TapisException if the tokens cannot be generated for any reason
     */
    public ServiceJWT(ServiceJWTParms parms, String servicePassword)
     throws TapisException, TapisClientException
    {
        // Unpack the parms object.
        _serviceName = parms.getServiceName();
        _tenant = parms.getTenant();
        _tokensBaseUrl = parms.getTokensBaseUrl();
        _accessTTL = parms.getAccessTTL();
        _refreshTTL = parms.getRefreshTTL();
        _delegationTenant = parms.getDelegationSubjectTenant();
        _delegationUser = parms.getDelegationSubjectUser();
        _additionalClaims = getClaimsAsJson(parms.getAdditionalClaims());
        
        // Validate input.
        validateInputs();
        
        // Create the service jwt.
        _tokPkg = createServiceJWT(servicePassword);
        
        // Start the refresh thread.
        startTokenRefreshThread();
    }
    
    /* **************************************************************************** */
    /*                               Public Accessors                               */
    /* **************************************************************************** */
    // Original inputs.
    @Override
    public String getServiceName() {return _serviceName;}
    @Override
    public String getTenant() {return _tenant;}
    @Override
    public String getTokensBaseUrl() {return _tokensBaseUrl;}
    @Override
    public TokenResponsePackage getTokPkg() {return _tokPkg;}
    @Override
    public int getAccessTTL() {return _accessTTL;}
    @Override
    public int getRefreshTTL() {return _refreshTTL;}
    @Override
    public String getDelegationTenant() {return _delegationTenant;}
    @Override
    public String getDelegationUser() {return _delegationUser;}
    @Override
    public String getAdditionalClaims() {return _additionalClaims;}
    
    // Generated access token information.  There's no chance
    // of the tokens package being null nor its access token.
    @Override
    public String getAccessJWT() {
        return _tokPkg.getAccessToken().getAccessToken();
    }
    @Override
    public Instant getAccessExpiresAt() {
        return _tokPkg.getAccessToken().getExpiresAt();
    }
    @Override
    public Integer getAccessExpiresIn() {
        return _tokPkg.getAccessToken().getExpiresIn();
    }
    
    /* **************************************************************************** */
    /*                               Private Accessors                              */
    /* **************************************************************************** */
    // Generated access token information.  There's no chance
    // of the tokens package being null nor its access token.
    private String getRefreshJWT() {
        return _tokPkg.getRefreshToken().getRefreshToken();
    }
    
    /* **************************************************************************** */
    /*                                Public Methods                                */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* hasExpiredAccessJWT:                                                         */
    /* ---------------------------------------------------------------------------- */
    /** The service application using this class to manage its service tokens can
     * poll this method to determine if the access token has expired.  If it has,
     * this object is no longer operable and should be discarded and replaced by a
     * newly constructed ServiceJWT instance.
     * 
     * @return true if the access token is expired, false if still useable
     */
    @Override
    public boolean hasExpiredAccessJWT() {
        return Instant.now().isAfter(getAccessExpiresAt());
    }
    
    /* ---------------------------------------------------------------------------- */
    /* interrupt:                                                                   */
    /* ---------------------------------------------------------------------------- */
    /** Interrupt the token refresh thread.  The method causes the token refresh
     * to exit and the current access JWT will eventually expire if it hasn't already
     * expired. 
     */
    public void interrupt()
    {
        _refreshThread.interrupt();
    }
    
    /* ---------------------------------------------------------------------------- */
    /* getRefreshCount:                                                             */
    /* ---------------------------------------------------------------------------- */
    @Override
    public int getRefreshCount() {return _refreshCount;}

    /* ---------------------------------------------------------------------------- */
    /* getLastRefreshTime:                                                          */
    /* ---------------------------------------------------------------------------- */
    @Override
    public Instant getLastRefreshTime() {return _lastRefreshTime;}

    /* **************************************************************************** */
    /*                                Private Methods                               */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* createServiceJWT:                                                            */
    /* ---------------------------------------------------------------------------- */
    private TokenResponsePackage createServiceJWT(String password) 
     throws TapisException, TapisClientException
    {
        // Check parameters.
        if (StringUtils.isBlank(password)) {
            String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "createServiceJWT", 
                                         "password");
            _log.error(msg);
            throw new TapisException(msg);
        }
        
        // Create and populate the client parameter object.
        var createParms = new CreateTokenParms();
        createParms.setTokenTenantId(_tenant);
        createParms.setTokenUsername(_serviceName);
        createParms.setAccountType(AccountTypeEnum.SERVICE);
        createParms.setGenerateRefreshToken(true);
        createParms.setAccessTokenTtl(_accessTTL);
        createParms.setRefreshTokenTtl(_refreshTTL);
        createParms.setClaims(_additionalClaims);
        
        // Are we delegating?
        if (StringUtils.isNotBlank(_delegationTenant)) {
            createParms.setDelegationToken(Boolean.TRUE);
            createParms.setDelegationSubTenantId(_delegationTenant);
            createParms.setDelegationSubUsername(_delegationUser);
        }
        
        // Get the client.
        var client = new TokensClient(_tokensBaseUrl);
        
        // Add basic auth header.
        String authString = _serviceName + ":" + password;
        String encodedString = Base64.getEncoder().encodeToString(authString.getBytes());
        client.addDefaultHeader("Authorization", "Basic " + encodedString);
        
        // Create the token package, which is always non-null.
        var tokPkg = client.createToken(createParms);
        
        // Validate result.
        if (!tokPkg.isValidAccessToken()) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_BAD_TOKEN_RESP", "access",
                                         createParms.getTokenUsername(),
                                         createParms.getTokenTenantId());
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (!tokPkg.isValidRefreshToken()) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_BAD_TOKEN_RESP", "refresh",
                                         createParms.getTokenUsername(),
                                         createParms.getTokenTenantId());
            _log.error(msg);
            throw new TapisException(msg);
        }
        
        // Success.
        return tokPkg;
    }
    
    /* ---------------------------------------------------------------------------- */
    /* refreshServiceJWT:                                                           */
    /* ---------------------------------------------------------------------------- */
    private TokenResponsePackage refreshServiceJWT() 
     throws TapisException, TapisClientException
    {
        // Create and populate the client parameter object.
        var refreshParms = new RefreshTokenParms();
        refreshParms.setRefreshToken(getRefreshJWT());
        
        // Get the client.
        var client = new TokensClient(_tokensBaseUrl);
        
        // Create the token package, which is always non-null.
        var tokPkg = client.refreshToken(refreshParms);
        
        // Validate result.
        if (!tokPkg.isValidAccessToken()) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_BAD_TOKEN_RESP", "access",
                                         _serviceName, _tenant);
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (!tokPkg.isValidRefreshToken()) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_BAD_TOKEN_RESP", "refresh",
                                        _serviceName, _tenant);
            _log.error(msg);
            throw new TapisException(msg);
        }
        
        // Success.
        return tokPkg;
    }
    
    /* ---------------------------------------------------------------------------- */
    /* validateInputs:                                                              */
    /* ---------------------------------------------------------------------------- */
    /** Throw an exception if any input does not validate.
     * 
     * @throws TapisException on validation error
     */
    private void validateInputs() throws TapisException
    {
        // Check string fields.
        if (StringUtils.isBlank(_tenant)) {
            String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateInputs", 
                                         "tenant");
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (StringUtils.isBlank(_serviceName)) {
            String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateInputs", 
                                         "serviceName");
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (StringUtils.isBlank(_tokensBaseUrl)) {
            String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateInputs", 
                                         "tokensBaseUrl");
            _log.error(msg);
            throw new TapisException(msg);
        }
        
        // Check numeric fields.  Make sure the refresh token expires no
        // earlier than the access token.
        if (_accessTTL < 1) {
            String msg = MsgUtils.getMsg("TAPIS_PARAMETER_LESS_THAN_MIN",
                                         "accessTTL", _accessTTL, 1);
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (_refreshTTL < 1) {
            String msg = MsgUtils.getMsg("TAPIS_PARAMETER_LESS_THAN_MIN",
                                         "refreshTTL", _refreshTTL, 1);
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (_refreshTTL < _accessTTL) {
            String msg = MsgUtils.getMsg("TAPIS_PARAMETER_LESS_THAN_MIN",
                    "refreshTTL", _refreshTTL, _accessTTL + " (accessTTL)");
            _log.error(msg);
            throw new TapisException(msg);
        }
        
        // Check delegation completeness.
        if (StringUtils.isBlank(_delegationTenant) && !StringUtils.isBlank(_delegationUser)) {
            String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateInputs", 
                                         "delegationTenant");
            _log.error(msg);
            throw new TapisException(msg);
        }
        if (!StringUtils.isBlank(_delegationTenant) && StringUtils.isBlank(_delegationUser)) {
            String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateInputs", 
                                         "delegationUser");
            _log.error(msg);
            throw new TapisException(msg);
        }
    }
    
    /* ---------------------------------------------------------------------------- */
    /* getClaimsAsJson:                                                             */
    /* ---------------------------------------------------------------------------- */
    /** Validate the claims and return a json string of the map.
     * 
     * @param claims the jwt claims or null
     * @return null or a json string
     * @throws TapisException on validation or serialization error
     */
    private String getClaimsAsJson(Map<String,Object> claims)
     throws TapisException
    {
        // Usual case.
        if (claims == null || claims.isEmpty()) return null;
        
        // Validate each claim key.  All null, primitive, 
        // array and object values are accepted. 
        for (var entry : claims.entrySet()) {
            // Make sure the key is reasonable.
            if (StringUtils.isBlank(entry.getKey())) {
                String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "getClaimsAsJson", 
                                             "claim entry");
                _log.error(msg);
                throw new TapisException(msg);
            }
        }
        
        // Write out the json representation that allows for null values.
        try {return TapisGsonUtils.getGson().toJson(claims);}
            catch (Exception e) {
                String msg = MsgUtils.getMsg("TAPIS_JSON_SERIALIZATION_ERROR", 
                                             claims.getClass().getSimpleName(), 
                                             e.getMessage());
                _log.error(msg, e);
                throw new TapisException(msg);
            }
    }

    /* ---------------------------------------------------------------------- */
    /* startTokenRefreshThread:                                               */
    /* ---------------------------------------------------------------------- */
    private void startTokenRefreshThread()
    {
        // Create and start the daemon thread only AFTER token is first acquired.
        var threadGroup = new ThreadGroup(THREADGROUP_NAME);
        _refreshThread = new TokenRefreshThread(threadGroup, REFRESH_THREAD_NAME);
        _refreshThread.setDaemon(true);
        _refreshThread.setUncaughtExceptionHandler(this);
        _refreshThread.start();
    }

    /* ---------------------------------------------------------------------- */
    /* uncaughtException:                                                     */
    /* ---------------------------------------------------------------------- */
    /** Note the unexpected death of our refresh thread.  We just let it die
     * and wait for the token to eventually expire, which will cause our service
     * to become unhealthy. 
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) 
    {
        // Record the error.
        _log.error(MsgUtils.getMsg("TAPIS_THREAD_UNCAUGHT_EXCEPTION", 
                                   t.getName(), e.toString()));
        e.printStackTrace(); // stderr for emphasis
    }
    
    /* **************************************************************************** */
    /*                            TokenRefreshThread Class                          */
    /* **************************************************************************** */
    /** This inner class will refresh the service token.  The enclosing class's 
     * token package field is atomically updated upon successful token refresh.
     */
    private final class TokenRefreshThread
     extends Thread
    {
        /* ---------------------------------------------------------------------- */
        /* constructor:                                                           */
        /* ---------------------------------------------------------------------- */
        private TokenRefreshThread(ThreadGroup threadGroup, String threadName) 
        {
            super(threadGroup, threadName);
        }
        
        /* ---------------------------------------------------------------------- */
        /* run:                                                                   */
        /* ---------------------------------------------------------------------- */
        /** Indefinitely refresh the access toking using the refresh token.  The
         * refresh token is guaranteed to expire no earlier than the access token.
         * The first token refresh operation on any access token will occur 
         * REFRESH_TIME_MILLIS before that token expires.  If the refresh fails, 
         * retries will generally occur every DFT_REFRESH_RETRY_MILLIS until the
         * access token expires.  
         * 
         * If the access token expires, this thread ends and the enclosing ServieJWT
         * class is no longer useful and hasExpiredAccessToken() will return true. 
         */
        @Override
        public void run() 
        {
            // We iterate in this loop each time a new token pair is acquired.
            boolean doRefresh = true;
            while (doRefresh) {
                // Calculate the time to wait for a new token.
                long newTokenWaitMillis = calculateNewTokenWaitMillis();
                
                // Refresh the token after the wait time expires.
                doRefresh = refreshToken(newTokenWaitMillis);
            }
            
            // Termination message.
            if (_log.isInfoEnabled()) {
                String msg = MsgUtils.getMsg("TAPIS_TOKEN_REFRESH_THREAD_TERMINATING", 
                                             _serviceName, _tenant, 
                                             Thread.currentThread().getName());
                _log.info(msg);
            }
        }
        
        /* ---------------------------------------------------------------------- */
        /* refreshToken:                                                          */
        /* ---------------------------------------------------------------------- */
        /** Wait a specified amount of time before trying to refresh the access
         * token.
         * 
         * @param sleepMillis milliseconds to wait before trying to refresh
         * @return true if a new access token was acquired before the current
         *              access token expired, false otherwise
         */
        private boolean refreshToken(long sleepMillis)
        {
            // Retry until the access token expires.
            while (true) {
                
                // Should we even bother?
                if (sleepMillis <= 0 && _log.isWarnEnabled()) {
                    String msg = MsgUtils.getMsg("TAPIS_TOKEN_REFRESH_TIMEOUT",
                                                _serviceName, _tenant, 
                                                Thread.currentThread().getName(),
                                                sleepMillis);
                    _log.warn(msg);
                    return false;
                }
                
                // Info message.
                if (_log.isInfoEnabled()) {
                    String msg = MsgUtils.getMsg("TAPIS_TOKEN_REFRESH_WAIT",
                                                 _serviceName, _tenant, 
                                                 Thread.currentThread().getName(),
                                                 sleepMillis);
                    _log.info(msg);
                }
 
                // Sleep for the prescribed time unless this thread was interrupted.
                // There is a period of time between the check and the sleep call 
                // in which the interrupt would go unnoticed until the next iteration.
                if (_refreshThread.isInterrupted()) return false;
                try {Thread.sleep(sleepMillis);}
                catch (InterruptedException e) {
                    if (_log.isWarnEnabled()) {
                        String msg = MsgUtils.getMsg("TAPIS_TOKEN_REFRESH_THREAD_INTERRUPTED",
                                                     _serviceName, _tenant,
                                                     Thread.currentThread().getName());
                        _log.warn(msg);
                    }
                    return false;
                }
                
                // Refresh the access token.
                TokenResponsePackage tokPkg = null;
                try {tokPkg = refreshServiceJWT();}
                    catch (Exception e) {
                        // Log the exception.
                        String msg = MsgUtils.getMsg("TAPIS_TOKEN_REFRESH_ERROR",
                                                     _serviceName, _tenant, 
                                                     Thread.currentThread().getName(),
                                                     sleepMillis);
                        _log.error(msg, e);
                        
                        // Let's try to schedule a retry?
                        sleepMillis = calculateRetryMillis();
                        continue;
                    }
                
                // The refresh succeeded and was validated. Atomically 
                // update the enclosing class's tokens by reassigning
                // the field to point to the new token package.  The 
                // other two assignments are also safe, but the group
                // of three assignments is not an atomic transaction.
                // We should be fine--there's nothing critical going 
                // on here.
                _tokPkg = tokPkg;
                _refreshCount++;
                _lastRefreshTime = Instant.now();
                return true;
            }
        }
        
        /* ---------------------------------------------------------------------- */
        /* calculateNewTokenWaitMillis:                                           */
        /* ---------------------------------------------------------------------- */
        /** This method calculates the number of milliseconds the refresh thread 
         * should sleep before making its first attempt to refresh the current access 
         * token.  The time should be no more than 30 minutes before the access
         * token is actually scheduled to expire.  This gives us some opportunity
         * to retry the refresh operation before expiration.
         * 
         * @return milliseconds to sleep before first refresh attempt
         */
        private long calculateNewTokenWaitMillis()
        {
            // Subtract the current time from the expiration time.
            Instant sub = getAccessExpiresAt().minusMillis(Instant.now().toEpochMilli());
            long subMillis = sub.toEpochMilli();
            if (subMillis <= 0) return 0; // Already expired.
            
            // Wake up a constant amount of time before the expiration time unless that
            // constant offset is too large.  In that case, we simply wait a preset 
            // fraction of the remaining time. 
            long refreshMillis = subMillis - REFRESH_TIME_MILLIS;
            if (refreshMillis <= 0) refreshMillis = (long)(subMillis * REFRESH_SHORT_FRACTION);
            
            return refreshMillis;
        }
        
        /* ---------------------------------------------------------------------- */
        /* calculateRetryMillis:                                                  */
        /* ---------------------------------------------------------------------- */
        /** After the first attempt at a refresh fails, we retry every retry_millis
         * (or less if the access token would expire before retry_millis) until the
         * access token expires.
         * 
         * @return the millisecond to wait before retrying
         */
        private long calculateRetryMillis()
        {
            // Has time expired?
            Instant sub = getAccessExpiresAt().minusMillis(Instant.now().toEpochMilli());
            long subMillis = sub.toEpochMilli();
            if (subMillis <= 0) return 0; // Already expired.
            
            // Try every retry interval or one last time the token will expire
            // before the default retry interval would end.
            return Math.min(subMillis-MIN_REFRESH_MILLIS, DFT_REFRESH_RETRY_MILLIS);
        }
    } // TokenRefreshThread
}
