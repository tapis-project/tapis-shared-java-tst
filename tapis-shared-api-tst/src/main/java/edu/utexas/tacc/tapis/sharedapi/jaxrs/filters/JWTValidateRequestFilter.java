package edu.utexas.tacc.tapis.sharedapi.jaxrs.filters;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;

import javax.annotation.Priority;
import javax.annotation.security.PermitAll;
import javax.validation.constraints.NotNull;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.shared.exceptions.TapisSecurityException;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shared.parameters.TapisEnv;
import edu.utexas.tacc.tapis.shared.parameters.TapisEnv.EnvVar;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext.AccountType;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadLocal;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.sharedapi.security.ITenantManager;
import edu.utexas.tacc.tapis.sharedapi.security.TapisSecurityContext;
import edu.utexas.tacc.tapis.sharedapi.security.TenantManager;
import edu.utexas.tacc.tapis.sharedapi.utils.TapisRestUtils;
import edu.utexas.tacc.tapis.tenants.client.gen.model.Tenant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;

/** This jax-rs filter is the main authentication mechanism for Tapis services 
 * written in Java.  This class depends on the Tapis Tenants service to acquire
 * the public keys of all tenants.  The Tenants service is accessed through the
 * TenantManager class.  The public keys are used to validate JWT signatures.
 * Additional tenant information is used to authorize tenants to act on behalf
 * of other tenants. 
 * 
 * This filter performs the following:
 * 
 *      - Reads the tapis jwt assertion header from the http request.
 *      - Determines whether the header is required and takes further action.
 *      - Extracts the tenant id from the unverified claims.
 *      - Optionally verifies the JWT signature using a tenant-specific key.
 *      - Enforces service and user token semantics.       
 *      - Extracts the user name and other values from the JWT claims.
 *      - Assigns claim values to their thread-local fields.
 *      - Assigns security related header values to their thread-local fields.
 *  
 * This class caches tenant public keys after it decodes them the first time.  
 * It inspects the TenantManager's last update time to determine if is cache
 * might be stale and, if so, clears the caches.  Tenant information rarely 
 * changes, but the information cached in this class automatically stays in
 * sync with the TenantManager, no restarts or manual intervention required.
 *      
 * The test parameter filter run after this filter and may override the values
 * set by this filter.
 * 
 * @author rcardone
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JWTValidateRequestFilter 
 implements ContainerRequestFilter
{
    /* ********************************************************************** */
    /*                               Constants                                */
    /* ********************************************************************** */
    // Tracing.
    private static final Logger _log = LoggerFactory.getLogger(JWTValidateRequestFilter.class);
    
    // Header key for jwts.
    private static final String TAPIS_JWT_HEADER     = "X-Tapis-Token";
    private static final String TAPIS_TENANT_HEADER  = "X-Tapis-Tenant";
    private static final String TAPIS_USER_HEADER    = "X-Tapis-User";
    private static final String TAPIS_HASH_HEADER    = "X-Tapis-User-Token-Hash";
    
    // Tapis claim keys.
    private static final String CLAIM_TENANT         = "tapis/tenant_id";
    private static final String CLAIM_USERNAME       = "tapis/username";
    private static final String CLAIM_TOKEN_TYPE     = "tapis/token_type";
    private static final String CLAIM_ACCOUNT_TYPE   = "tapis/account_type";
    private static final String CLAIM_DELEGATION     = "tapis/delegation";
    private static final String CLAIM_DELEGATION_SUB = "tapis/delegation_sub";
    
    // No-auth openapi resource names.
    private static final String OPENAPI_JSON = "/openapi.json";
    private static final String OPENAPI_YAML = "/openapi.yaml";
    
    // The token types this filter expects.
    private static final String TOKEN_ACCESS = "access";
    
    /* ********************************************************************** */
    /*                                Fields                                  */
    /* ********************************************************************** */
    @Context
    private ResourceInfo resourceInfo;
    
    // Cache of tenant public keys, mapping tenant id to public key.
    // All access to this map must be limited to one thread at a time.
    private static final HashMap<String,PublicKey> _keyCache = new HashMap<>();
    
    // A real or mocked tenant manager object.
    private ITenantManager _tenantManager;
    
    /* ********************************************************************** */
    /*                            Constructors                                */
    /* ********************************************************************** */
    /* ---------------------------------------------------------------------- */
    /* constructor:                                                           */
    /* ---------------------------------------------------------------------- */
    /** This is the constructor used everywhere except for unit testing.  It
     * assumes that the real TenantManager class will have been initialized
     * by the time the first request comes through.  All request filters 
     * constructed in this manner have their _tenantManager field assigned
     * during processing.  Attempting to assign the field here causes an
     * exception because of the order in which JAX-RS does things.
     */
    public JWTValidateRequestFilter() {}
    
    /* ---------------------------------------------------------------------- */
    /* constructor:                                                           */
    /* ---------------------------------------------------------------------- */
    /** This is a unit test only constructor.  Callers are able to substitute
     * any mock tenant manager they would like by calling this constructor.
     * 
     * Attempting to put an Inject annotation on this constructor causes JAX-RS
     * to get throw numerous exceptions, so it's up to the caller to integrate
     * this constructor into their test harness.  
     * 
     * @param tenantManager a mock object
     */
    public JWTValidateRequestFilter(@NotNull ITenantManager tenantManager)
    {
        // Assign a mock tenant manager instance provided explicitly by the caller.
        _tenantManager = tenantManager;
    }
    
    /* ********************************************************************** */
    /*                            Public Methods                              */
    /* ********************************************************************** */
    /* ---------------------------------------------------------------------- */
    /* filter:                                                                */
    /* ---------------------------------------------------------------------- */
    @Override
    public void filter(ContainerRequestContext requestContext) 
    {
        // Tracing.
        if (_log.isTraceEnabled())
            _log.trace("Executing JAX-RX request filter: " + this.getClass().getSimpleName() + ".");

        // OPTIONS requests should not have any authentication
        if (requestContext.getMethod().equals(HttpMethod.OPTIONS)) return;

        // @PermitAll on the method takes precedence over @RolesAllowed on the class, allow all
        // requests with @PermitAll to go through
        if (resourceInfo.getResourceMethod().isAnnotationPresent(PermitAll.class)) return;

        // Skip JWT processing for non-authenticated requests.
        if (isNoAuthRequest(requestContext)) return;

        // ------------------------ Extract Encoded JWT ------------------------
        // Assign the default tenant manager instance that is a singleton expected 
        // to already exist.  This field is not null when a mock object is provided
        // on construction during unit testing.
        if (_tenantManager == null) _tenantManager = TenantManager.getInstance();
        
        // Parse variables.
        String encodedJWT = null;
        
        // Extract the jwt header from the set of headers. 
        // We expect the key search to be case insensitive.
        MultivaluedMap<String, String> headers = requestContext.getHeaders();
        encodedJWT = headers.getFirst(TAPIS_JWT_HEADER);
            
        // Make sure that a JWT was provided when it is required.
        if (StringUtils.isBlank(encodedJWT)) {
            // This is an error in production, but allowed when running in test mode.
            // We let the endpoint verify that all needed parameters have been supplied.
            boolean jwtOptional = TapisEnv.getBoolean(EnvVar.TAPIS_ENVONLY_JWT_OPTIONAL);
            if (jwtOptional) return;
            
            // We abort the request because we're missing required security information.
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_MISSING_JWT_INFO", requestContext.getMethod());
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        
        // ------------------------ Read Tenant Claim --------------------------
        // Get the JWT without verifying the signature.  Decoding checks that
        // the token has not expired.
        @SuppressWarnings("rawtypes")
        Jwt unverifiedJwt = null;
        try {unverifiedJwt = decodeJwt(encodedJWT);}
        catch (Exception e) {
            // Preserve the decoder method's message.
            String msg = e.getMessage();
            _log.error(msg); // No need to log the stack trace again.
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        
        // Get the claims.
        Claims claims = null;
        try {claims = (Claims) unverifiedJwt.getBody();}
        catch (Exception e) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_GET_CLAIMS", unverifiedJwt);
            _log.error(msg, e);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        if (claims == null) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_NO_CLAIMS", unverifiedJwt);
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        
        // Retrieve the user name from the claims section.
        String jwtTenant = (String)claims.get(CLAIM_TENANT);
        if (StringUtils.isBlank(jwtTenant)) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_CLAIM_NOT_FOUND", unverifiedJwt, 
                                         CLAIM_TENANT);
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
            
        // ------------------------ Verify JWT ---------------------------------
        // Do we need to verify the JWT?
        boolean skipJWTVerify = TapisEnv.getBoolean(EnvVar.TAPIS_ENVONLY_SKIP_JWT_VERIFY);
        if (!skipJWTVerify) {
            try {verifyJwt(encodedJWT, jwtTenant);}
            catch (Exception e) {
                Status status = Status.UNAUTHORIZED;
                String msg = e.getMessage();
                if (msg.startsWith("TAPIS_SECURITY_JWT_KEY_ERROR"))
                    status = Status.INTERNAL_SERVER_ERROR;
                _log.error(e.getMessage(), e);
                requestContext.abortWith(Response.status(status).entity(e.getMessage()).build());
                return;
            }
        }
        
        // ------------------------ Validate Claims ----------------------------
        // Check that the token is always an access token.
        String tokenType = (String)claims.get(CLAIM_TOKEN_TYPE);
        if (StringUtils.isBlank(tokenType) || !TOKEN_ACCESS.contentEquals(tokenType)) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_INVALID_CLAIM", CLAIM_TOKEN_TYPE,
                                         tokenType);
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        
        // Check the account type.
        String accountTypeStr = (String)claims.get(CLAIM_ACCOUNT_TYPE);
        if (StringUtils.isBlank(accountTypeStr)) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_INVALID_CLAIM", CLAIM_ACCOUNT_TYPE,
                                         accountTypeStr);
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        AccountType accountType = null;
        try {accountType = AccountType.valueOf(accountTypeStr);}
        catch (Exception e) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_INVALID_CLAIM", CLAIM_ACCOUNT_TYPE,
                                         accountTypeStr);
            _log.error(msg, e);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
        
        // Get the user.
        String jwtUser = (String)claims.get(CLAIM_USERNAME);
        if (StringUtils.isBlank(jwtUser)) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_INVALID_CLAIM", CLAIM_USERNAME, jwtUser);
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return;
        }
       
        // Get the delegation information if it exists.
        String delegator = null;
        Boolean delegation = (Boolean)claims.get(CLAIM_DELEGATION);
        if (delegation != null && delegation) {
            delegator = (String)claims.get(CLAIM_DELEGATION_SUB);
            if (!TapisRestUtils.checkJWTSubjectFormat(delegator)) {
                String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_INVALID_CLAIM", CLAIM_DELEGATION_SUB,
                                             delegator);
                _log.error(msg);
                requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
                return;
            }
            
            // Get the tenant component of the user@tenant string.  The  
            // above validation call guarantees that this won't blow up.
            String delegationTenant = delegator.substring(delegator.indexOf('@') + 1);
            
            // Check that the jwt tenant is allowed to act on behalf of the delegation tenant.
            // If false if returned, the called method has already modified the context to 
            // abort the request, in which case we immediately return from here.
            if (!allowTenant(requestContext, jwtUser, jwtTenant, delegationTenant)) return;
        }
        
        // ------------------------ Assign Header Values -----------------------
        // Get information that may have been relayed in request headers.
        String headerUserTokenHash = headers.getFirst(TAPIS_HASH_HEADER);
        
        // These headers are only required on service tokens.
        String oboTenantId = null;
        String oboUser     = null;
        if (accountType == AccountType.service) {
            // These headers should always be set.
            oboTenantId = headers.getFirst(TAPIS_TENANT_HEADER);
            oboUser     = headers.getFirst(TAPIS_USER_HEADER);
            
            // The X-Tapis-User header is mandatory when a service jwt is used.
            if (StringUtils.isBlank(oboUser)) {
                String msg = MsgUtils.getMsg("TAPIS_SECURITY_MISSING_HEADER", jwtUser, jwtTenant,
                                             accountType.name(), TAPIS_USER_HEADER);
                _log.error(msg);
                requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
                return;
            }
            
            // The X-Tapis-User header is mandatory when a service jwt is used.
            if (StringUtils.isBlank(oboTenantId)) {
                String msg = MsgUtils.getMsg("TAPIS_SECURITY_MISSING_HEADER", jwtUser, jwtTenant,
                                             accountType.name(), TAPIS_TENANT_HEADER);
                _log.error(msg);
                requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
                return;
            }
            
            // Check that the jwt tenant is allowed to act on behalf of the header tenant.
            // If false if returned, the called method has already modified the context to 
            // abort the request, in which case we immediately return from here.
            if (!allowTenant(requestContext, jwtUser, jwtTenant, oboTenantId)) return;
        } else {
            // Account type is user. Make sure the user header is not present.
            if (StringUtils.isNotBlank(oboUser)) {
                String msg = MsgUtils.getMsg("TAPIS_SECURITY_UNEXPECTED_HEADER", jwtUser, 
                                             jwtTenant, accountType.name(), TAPIS_USER_HEADER);
                _log.error(msg);
                requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
                return;
            }
            
            // Set the on-behalf-of values to the jwt claim values with user tokens.
            oboTenantId = jwtTenant;
            oboUser     = jwtUser;
        }
        
        // ------------------------ Assign Effective Values --------------------
        // Assign pertinent claims and header values to our threadlocal context.
        TapisThreadContext threadContext = TapisThreadLocal.tapisThreadContext.get();
        threadContext.setJwtTenantId(jwtTenant);           // from jwt claim, never null
        threadContext.setJwtUser(jwtUser);                 // from jwt claim, never null
        threadContext.setOboTenantId(oboTenantId);         // from jwt or header, never null
        threadContext.setOboUser(oboUser);                 // from jwt or header, never null
        threadContext.setAccountType(accountType);         // from jwt, never null
        threadContext.setDelegatorSubject(delegator);      // from jwt, can be null
        threadContext.setUserJwtHash(headerUserTokenHash); // from header, can be null

        // Inject the user and JWT into the security context and request context
        AuthenticatedUser requestUser = 
            new AuthenticatedUser(jwtUser, jwtTenant, accountTypeStr, 
                                  delegator, oboUser, oboTenantId, 
                                  headerUserTokenHash, encodedJWT);
        requestContext.setSecurityContext(new TapisSecurityContext(requestUser));
    }

    /* ********************************************************************** */
    /*                            Private Methods                             */
    /* ********************************************************************** */
    /* ---------------------------------------------------------------------- */
    /* decodeJwt:                                                             */
    /* ---------------------------------------------------------------------- */
    /** Decode the jwt without verifying its signature.
     * 
     * @param encodedJWT the JWT from the request header
     * @return the decoded but not verified jwt
     * @throws TapisSecurityException on error
     */
    @SuppressWarnings("rawtypes")
    private Jwt decodeJwt(String encodedJWT)
     throws TapisSecurityException
    {
        // Some defensive programming.
        if (encodedJWT == null) return null;
        
        // Lop off the signature part of the encoding so that the 
        // jjwt library can parse it without attempting validation.
        // We expect the jwt to contain exactly two periods in 
        // the following encoded format: header.body.signature
        // We need to remove the signature but leave both periods.
        String remnant = encodedJWT;
        int lastDot = encodedJWT.lastIndexOf(".");
        if (lastDot + 1 < encodedJWT.length()) // should always be true
            remnant = encodedJWT.substring(0, lastDot + 1);
        
        // Parse the header and claims. If for some reason the remnant
        // isn't of the form header.body. then parsing will fail.
        Jwt jwt = null;
        try {jwt = Jwts.parser().parse(remnant);}
            catch (Exception e) {
                // The decode may have detected an expired JWT.
                String msg;
                String emsg = e.getMessage();
                if (emsg != null && emsg.startsWith("JWT expired at")) 
                    msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_EXPIRED", emsg);
                  else msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_PARSE_ERROR", emsg);
                
                _log.error(msg, e);
                throw new TapisSecurityException(msg, e);
            }
        return jwt;
    }
    
    /* ---------------------------------------------------------------------- */
    /* verifyJwt:                                                             */
    /* ---------------------------------------------------------------------- */
    /** Verify the jwt as it was received as a header value.  Signature verification
     * occurs using the specified tenant's signing key.  An exception is thrown
     * if decoding or signature verification fails.
     * 
     * @param encodedJwt the raw jwt
     * @param tenant the tenant to verify against
     * @throws TapisSecurityException if the jwt cannot be verified 
     */
    private void verifyJwt(String encodedJwt, String tenant) 
     throws TapisSecurityException
    {
        // Get the public part of the signing key.
        PublicKey publicKey = getJwtPublicKey(tenant);
        
        // Verify and import the jwt data.
        @SuppressWarnings({ "unused", "rawtypes" })
        Jwt jwt = null; 
        try {jwt = Jwts.parser().setSigningKey(publicKey).parse(encodedJwt);}
            catch (Exception e) {
                String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_PARSE_ERROR", e.getMessage());
                _log.error(msg, e);
                throw new TapisSecurityException(msg, e);
            }
    }
    
    /* ---------------------------------------------------------------------- */
    /* getJwtPublicKey:                                                       */
    /* ---------------------------------------------------------------------- */
    /** Return the cached public key if it exists.  If it doesn't exist, load it
     * from the keystore, cache it, and then return it. 
     * 
     * The exceptions thrown by this method all use the TAPIS_SECURITY_JWT_KEY_ERROR
     * message.  This message is used by calling routines to distinguish between
     * server and requestor errors.
     * 
     * @param tenantId the tenant whose signature verification key is requested
     * @return the tenant's signature verification key
     * @throws TapisSecurityException on error
     */
    private PublicKey getJwtPublicKey(String tenantId)
     throws TapisSecurityException
     {
        // Get when the tenant information was last updated.
        Instant lastTenantUpdate = _tenantManager.getLastUpdateTime();
        
        // Synchronize access to the key cache across all instances of this class.
        synchronized (_keyCache) 
        {
            // ------------------- Check For Cached Key -------------------
            // See if we need to clear the cache because the tenant information has changed.
            if (lastTenantUpdate != null)  // should never be null but we check anyway
                if (Instant.now().isBefore(lastTenantUpdate)) _keyCache.clear();
                  else {
                      // Return the previously calculated public key if it exists.
                      PublicKey publicKey = _keyCache.get(tenantId);
                      if (publicKey != null) return publicKey;
                  }
            
            // ------------------- Decode New Key -------------------------
            // Get the tenant's public key as saved in the tenants table.
            Tenant tenant;
            try {tenant = _tenantManager.getTenant(tenantId);} 
                catch (Exception e) {
                    String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_KEY_ERROR", e.getMessage());
                    _log.error(msg, e);
                    throw new TapisSecurityException(msg, e);
                }
            
            // Trim prologue and epilogue if they are present.
            String encodedPublicKey = trimPublicKey(tenant.getPublicKey());
            
            // Decode the base 64 string.
            byte[] publicBytes;
            try {publicBytes = Base64.getDecoder().decode(encodedPublicKey);}
                catch (Exception e) {
                    String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_KEY_ERROR", e.getMessage());
                    _log.error(msg, e);
                    throw new TapisSecurityException(msg, e);
                }
            
            // Create the public key object from the byte array.
            PublicKey publicKey;
            try {
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                publicKey = keyFactory.generatePublic(keySpec);
            }
            catch (Exception e) {
                String msg = MsgUtils.getMsg("TAPIS_SECURITY_JWT_KEY_ERROR", e.getMessage());
                _log.error(msg, e);
                throw new TapisSecurityException(msg, e);
            }
        
            // Add the key to the cache before returning.
            _keyCache.put(tenantId, publicKey);
            return publicKey;
        }
     }
    
    /* ---------------------------------------------------------------------- */
    /* trimPublicKey:                                                         */
    /* ---------------------------------------------------------------------- */
    /** Remove the prologue and epilogue text if they exist from an base64 
     * encoded key string.
     * 
     * @param encodedPublicKey base64 encoded public key
     * @return trimmed base64 public key
     */
    private String trimPublicKey(String encodedPublicKey)
    {
        // This should never happen.
        if (encodedPublicKey == null) return "";
        
        // Remove prologue and epilogue if they exist.  The Tapis Tenants service
        // often stores keys with the following PEM prologue and epilogue (see
        // https://tools.ietf.org/html/rfc1421 for the specification):
        //
        //      "-----BEGIN PUBLIC KEY-----\n"
        //      "\n-----END PUBLIC KEY-----"
        //
        // In general, different messages can appear after the BEGIN and END text,
        // so stripping out the prologue and epilogue requires some care.  The  
        // approach below handles only unix-style line endings.  
        // 
        // Check for unix style prologue.
        int index = encodedPublicKey.indexOf("-\n");
        if (index > 0) encodedPublicKey = encodedPublicKey.substring(index + 2);
        
        // Check for unix style epilogue.
        index = encodedPublicKey.lastIndexOf("\n-");
        if (index > 0) encodedPublicKey = encodedPublicKey.substring(0, index);
        
        return encodedPublicKey;
    }
    
    /* ---------------------------------------------------------------------- */
    /* isNoAuthRequest:                                                       */
    /* ---------------------------------------------------------------------- */
    /** Return true if the requested uri is exempt from authentication.  These
     * request do not contain a JWT so no authentication is possible.
     * 
     * @param requestContext the request context
     * @return true is no authentication is required, false otherwise
     */
    private boolean isNoAuthRequest(ContainerRequestContext requestContext)
    {
        // Get the service-specific path, which is the path after the host:port 
        // segment and includes a leading slash.  
        String relativePath = requestContext.getUriInfo().getRequestUri().getPath();
        
        // Allow anyone to access the openapi requests.
        if (relativePath.endsWith(OPENAPI_JSON)) return true;
        if (relativePath.endsWith(OPENAPI_YAML)) return true; 
        
        // Authentication required.
        return false;
    }
    
    /* ---------------------------------------------------------------------- */
    /* allowTenant:                                                           */
    /* ---------------------------------------------------------------------- */
    /** Determine whether the tenant specified in the JWT can operate on behalf
     * of the new tenant.  This method will abort the request if the new tenant
     * is not allowed.  False is returned to immediately abort the request, true 
     * to continue request processing.
     * 
     * @param requestContext the context passed into this filter
     * @param jwtUser the user designated in the JWT
     * @param jwtTenantId the tenant designated in the JWT
     * @param newTenantId the substitute tenant
     * @return true if request processing can continue, false to abort
     */
    private boolean allowTenant(ContainerRequestContext requestContext, String jwtUser, 
                                String jwtTenantId, String newTenantId)
    {
        // Consult the jwt tenant definition for allowable tenants. 
        boolean allowedTenant;
        try {allowedTenant = TapisRestUtils.isAllowedTenant(jwtTenantId, newTenantId);}
        catch (Exception e) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_ALLOWABLE_TENANT_ERROR", 
                                         jwtUser, jwtTenantId, newTenantId);
            _log.error(msg, e);
            requestContext.abortWith(Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build());
            return false;
        }
        
        // Can the new tenant id be used by the jwt tenant?
        if (!allowedTenant) {
            String msg = MsgUtils.getMsg("TAPIS_SECURITY_TENANT_NOT_ALLOWED", 
                                         jwtUser, jwtTenantId, newTenantId);
            _log.error(msg);
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).entity(msg).build());
            return false;
        }
        
        // The new tenant is allowed.
        return true;
    }
    
}
