package edu.utexas.tacc.tapis.shared.parameters;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;

/** This class dynamically queries the OS environment variables for values.  The
 * pre-determined set of possible environment variables are defined in the EnvVar
 * enum.  This class provides a way of cataloging and documenting all environment
 * variables that are used in tapis.
 * 
 * @author rcardone
 *
 */
public class TapisEnv 
{
	/* **************************************************************************** */
	/*                                   Constants                                  */
	/* **************************************************************************** */
	// Local logger.
	private static final Logger _log = LoggerFactory.getLogger(TapisEnv.class);
	
	// Environment-only prefix.
	public static final String ENVONLY_KEY_PREFIX = "tapis.envonly.";
	
	// Cache commonly used data.
	private static String[] _loggingFilterPrefixes;
	private static String[] _loggingFilterSuffixes;
	
	/* **************************************************************************** */
	/*                                     Enums                                    */
	/* **************************************************************************** */
	public enum EnvVar 
	{
	  // *** All environment variables must start with "tapis." to be automatically 
	  // *** recognized by parameter parsing routines (see TapisInput).
	  //
	  // By convention, variable names that are specific to a single service should
	  // use the service name as its second component, such as tapis.files.
	  //
	  // Even though the variable keys defined here are referred to "environment
	  // variables", they can originate from sources other than the just theOS 
	  // environment.  In particular, these key/value pairs can be specified in property  
	  // files or as JVM command-line definitions.  To support dynamic value modification
	  // during execution, however, some key/value pairs are designated as "environment
	  // only" and will only be recognized when set in the OS environment.
	  //
	  // ENV-VARIABLE-ONLY
	  // -----------------
	  // Enums beginning with TAPIS_ENVONLY_PREFIX are expected to be set only in 
	  // the environment and each query should go to the environment to get the 
	  // parameter's current value.  These parameters can dynamically change 
	  // during service execution because code that accesses them should
	  // directly query the OS environment for their current value.  
	  // 
	  // The actual environment variable keys must begin with "tapis.envonly.".
	  // In addition, code that loads parameters from various sources should 
	  // ignore keys that begin with "tapis.envonly." when they are read from
	  // other sources (e.g., properties file, etc.).
	  
	  // -------------------- General Parameters -------------------------
	  // Comma or semi-colon separated list of servlet path names 
	  // prefixes for which logging filters should be turned on. 
	  TAPIS_REQUEST_LOGGING_FILTER_PREFIXES("tapis.request.logging.filter.prefixes"),
		
	  // Comma or semi-colon separated list of servlet path names 
	  // suffixes for which logging filters should be turned off. 
	  TAPIS_REQUEST_LOGGING_IGNORE_SUFFIXES("tapis.request.logging.ignore.suffixes"),
		
		// The path to a properties file that contains a service's input parameters.
	  TAPIS_SERVICE_PROPERTIES_PATHNAME("tapis.service.properties.pathname"),
	  
	  // The path to the queue definitions directory.  This directory contains 
	  // json files of the form <tenantId>.json.  Each file contains the job queue
	  // definitions for that tenant.
	  TAPIS_JOB_QUEUE_DEFINITION_DIR("tapis.job.queue.definition.dir"),
	  
	  // Short name that uniquely identifies the program instance executing.
	  // This name should be meaningful to humans, useful in creating identifiers
	  // and constructing names with host-level visibility.  Host-level visibility
	  // means names likely to be unique in the context of a host. For example, 
	  // "cyversejobs1" is an instance name that could be used in creating directories
	  // or in naming queues or exchanges.  The name should be no more than 26 
	  // characters long and contain letters and digits only.  This key does not 
	  // replace the TAPIS_INSTANCE_ID, which is a guuid and used where the uniqueness
	  // requirement is absolute.
	  TAPIS_INSTANCE_NAME("tapis.instance.name"),
	  
	  // Logging level of the Maverick Library code (3rd party code) during service startup
	  TAPIS_MAVERICK_LOG_LEVEL("tapis.maverick.log.level"),
	  
	  // The slf4j target directory for logs for web applications and programs.
	  // See the various logback.xml configuration files for usage details.
	  TAPIS_LOG_DIRECTORY("tapis.log.directory"),
	  
	  // The slf4j target log file name for web applications and programs.
	  // See the various logback.xml configuration files for usage details.
	  TAPIS_LOG_FILE("tapis.log.file"),
	  
	  // ------------------- SQL Database Parameters -------------------
	  // MySQL parameters.
	  TAPIS_DB_CONNECTION_POOL_SIZE("tapis.db.connection.pool.size"),
	  TAPIS_DB_USER("tapis.db.user"),
	  TAPIS_DB_PASSWORD("tapis.db.password"),
	  TAPIS_DB_JDBC_URL("tapis.db.jdbc.url"),
	  
	  // Set to zero or less to turn off database metering.
	  TAPIS_DB_METER_MINUTES("tapis.db.meter.minutes"),
	  
	  // ------------------- RabbitMQ Parameters -----------------------
	  // guest/guest is commonly used in non-production environments.
      TAPIS_QUEUE_USER("tapis.queue.user"),
      TAPIS_QUEUE_PASSWORD("tapis.queue.password"),
    
	  // Default is localhost.
	  TAPIS_QUEUE_HOST("tapis.queue.host"),
	  
	  // Default is 5672 for non-ssl, 5671 for ssl.
	  TAPIS_QUEUE_PORT("tapis.queue.port"),
	  
	  // Boolean, default is false.
	  TAPIS_QUEUE_SSL_ENABLE("tapis.queue.ssl.enable"),
	  
	  // Boolean, default is true.
	  TAPIS_QUEUE_AUTO_RECOVERY("tapis.queue.auto.recovery"),
	  
	  // ------------------- Notification Parameters -------------------
	  // Currently, only "BEANSTALK" is supported.
	  TAPIS_NOTIF_QUEUE_TYPE("tapis.notif.queue.type"),
	  
	  // The notification queue host.
	  TAPIS_NOTIF_HOST("tapis.notif.host"),
	  
	  // The port used on the notification queue host.
	  TAPIS_NOTIF_PORT("tapis.notif.port"),
	  
	  // The onto which messages that generate notifications are placed.
	  TAPIS_NOTIF_QUEUE_NAME("tapis.notif.queue.name"),
	  
	  // The retry queue name.
	  TAPIS_NOTIF_RETRY_QUEUE_NAME("tapis.notif.retry.queue.name"),
	  
	  // The topic queue name (not currently used).
	  TAPIS_NOTIF_TOPIC_NAME("tapis.notif.topic.name"),
	  
	  // ------------------- Mail Parameters ---------------------------
	  // One of the currently supported providers (SMTP or LOG).
	  TAPIS_MAIL_PROVIDER("tapis.mail.provider"),
	  
	  // True is authentication is required, false otherwise.
	  TAPIS_SMTP_AUTH("tapis.smtp.auth"),
	  
	  // The outgoing mail server hostname.
	  TAPIS_SMTP_HOST("tapis.smtp.host"),
	  
	  // The outgoing mail server port.
	  TAPIS_SMTP_PORT("tapis.smtp.port"),
	  
	  // The user name when authentication is required.
	  TAPIS_SMTP_USER("tapis.smtp.user"),
	  
	  // The password when authentication is required.
	  TAPIS_SMTP_PASSWORD("tapis.smtp.password"),
	  
	  // The sender's name.
	  TAPIS_SMTP_FROM_NAME("tapis.smtp.from.name"),
	  
	  // The sender's email address.
	  TAPIS_SMTP_FROM_ADDRESS("tapis.smtp.from.address"),
	  
	  // ------------------- Support Parameters ------------------------
	  // The name of the support person or organization that runs tapis.
	  TAPIS_SUPPORT_NAME("tapis.support.name"),
	  
      // The email address of tapis support.
      TAPIS_SUPPORT_EMAIL("tapis.support.email"),
      
      // ------------------- Tenant Service Parameters -----------------
      // The protocol and host/port of the Tapis installation's tenants service.
      TAPIS_TENANT_SVC_BASEURL("tapis.tenant.svc.baseurl"),
      
      // The password used by services to obtain their initial service JWT.
      TAPIS_SERVICE_PASSWORD("tapis.service.password"),
      
      // ------------------- SK Parameters -----------------------------
      // Whether to disable the secrets subsystem.
      TAPIS_SK_VAULT_DISABLE("tapis.sk.vault.disable"),
      
      // Whether to disable automatic vault token renewal.
      TAPIS_SK_VAULT_RENEWAL_DISABLE("tapis.sk.vault.renewal.disable"),
      
      // The address of the vault server (ex: http://myhost:8200)
      TAPIS_SK_VAULT_ADDRESS("tapis.sk.vault.address"),
      
      // The approle role id assigned by vault to SK.
      TAPIS_SK_VAULT_ROLE_ID("tapis.sk.vault.roleid"),
      
      // The short-lived approle secret id required for login.
      TAPIS_SK_VAULT_SECRET_ID("tapis.sk.vault.secretid"),
      
      // Vault connection timeout in seconds.
      TAPIS_SK_VAULT_OPEN_TIMEOUT("tapis.sk.vault.open.timeout"),
      
      // Vault response timeout in seconds after connection is established.
      TAPIS_SK_VAULT_READ_TIMEOUT("tapis.sk.vault.read.timeout"),
      
      // Whether http or https with cert should be used for vault communication.
      TAPIS_SK_VAULT_SSL_VERIFY("tapis.sk.vault.ssl.verify"),
	  
      // The file path to the pem certificate (see SslConfig).
      TAPIS_SK_VAULT_SSL_CERT_FILE("tapis.sk.vault.ssl.cert.file"),
      
      // The file path to the pem certificate (see SslConfig).
      TAPIS_SK_VAULT_SK_KEY_PEM_FILE("tapis.sk.vault.sk.key.pem.file"),
      
      // The time-to-live of SK vault token in seconds.  Must be less than or 
      // equal to the maximum number of seconds defined in the sk-role.json file.
      // Type is integer, default value is 28,800 (8 hours).
      TAPIS_SK_VAULT_TOKEN_RENEWAL_SECONDS("tapis.sk.vault.token.renewal.seconds"),
      
      // When to begin issuing token renewal requests as a percent of token total
      // ttl.  Up to 10 token renewal requests will be attempted, beginning
      // approximately at the renewal threshold point and re-attempted at equal 
      // intervals until attempts expire.  Type is integer, a percentage between 
      // 20 and 80, with a default value of 50.
      TAPIS_SK_VAULT_TOKEN_RENEWAL_THRESHOLD("tapis.sk.vault.token.renewal.threshold"),
      
	  // ------------------- Env Only Parameters -----------------------
      // ENV-VARIABLE-ONLY: The flag to log security information such as JWT headers.  
      // Boolean value, the default is false.
      TAPIS_ENVONLY_LOG_SECURITY_INFO(ENVONLY_KEY_PREFIX + "log.security.info"),
    
	  // ENV-VARIABLE-ONLY: Boolean parameter that allow test header parameters to be 
	  // recognized when processing various REST endpoints.  All such parameters start 
	  // with "X-Tapis-Test-" and closely match the Tapis JWT claims.  These parameters 
      // allow values to be passed to endpoints for testing purposes.  Setting this flag 
      // to false causes these parameters to ignored if they are present.  This setting
      // is usually used in conjunction with the JWT_OPTIONAL setting to test services
      // locally for different users without requiring a JWT.
      // Boolean value, the default is false.
	  TAPIS_ENVONLY_ALLOW_TEST_HEADER_PARMS(ENVONLY_KEY_PREFIX + "allow.test.header.parms"),
	      
	  // ENV-VARIABLE-ONLY: The flag that accepts requests that may not have a JWT header. 
	  // Set this flag to true in test environments in which JWT values are assigned
	  // using alternate methods.  Usually used in conjunction with the preceding setting.
	  // Boolean value, the default is false.
	  TAPIS_ENVONLY_JWT_OPTIONAL(ENVONLY_KEY_PREFIX + "jwt.optional"),
	  
	  // ENV-VARIABLE-ONLY: The flag that controls whether JWT signatures are used
	  // to validate the integrity of the JWT data.  If set to true, the JWT data
	  // is still accessed, but the signature verification step is skipped.
	  // Boolean value, the default is false.
	  TAPIS_ENVONLY_SKIP_JWT_VERIFY(ENVONLY_KEY_PREFIX + "skip.jwt.verify"),
	  
	  // ENV-VARIABLE-ONLY: The password to the tapis keystore.  This password is
	  // required to access the JWT public key certificate used to verify the 
	  // integrity of JWT's received in HTTP requests.  If JWT verification is
	  // being skipped, then this value does not need to be set.
	  // String value, no default.
	  TAPIS_ENVONLY_KEYSTORE_PASSWORD(ENVONLY_KEY_PREFIX + "keystore.password");
	    
	  
	  // The name of the actual environment variable in the OS.
	  private String _envName;
	  private EnvVar(String envName) {_envName = envName;}
	  public String getEnvName() {return _envName;}
	}
	
	/* **************************************************************************** */
	/*                                    Fields                                    */
	/* **************************************************************************** */
	// Create the pattern for a list of one or more comma or semi-colon separated 
	// names with no embedded whitespace.
	private static final Pattern _namePattern = Pattern.compile("[,;\\s]");
	
	/* **************************************************************************** */
	/*                                Public Methods                                */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
	/* get:                                                                         */
	/* ---------------------------------------------------------------------------- */
	/** Retrieve the environment variable as a string.
	 * 
	 * @param envVar the environment variable key
	 * @return the key's value or null if no value has been assigned. 
	 */
	public static String get(EnvVar envVar)
	{
		if (envVar == null) return null;
		return getEnvValue(envVar);
	}
	
	/* ---------------------------------------------------------------------------- */
	/* getBoolean:                                                                  */
	/* ---------------------------------------------------------------------------- */
	/** Retrieve the environment variable as a boolean.
	 * 
	 * @param envVar the environment variable key
	 * @return the key's value or false if the value doesn't exist or is invalid. 
	 */
	public static boolean getBoolean(EnvVar envVar)
	{
		if (envVar == null) return false;
		String s = getEnvValue(envVar);
		if (s == null) return false;
		try {return Boolean.valueOf(s);}
	      catch (Exception  e) {
	    	  _log.error(MsgUtils.getMsg("TAPIS_ENV_CONVERSION_FAILED", "Boolean", s));
	    	  return false;
	      }
	}
	
	/* ---------------------------------------------------------------------------- */
	/* getInteger:                                                                  */
	/* ---------------------------------------------------------------------------- */
	/** Retrieve the environment variable as an Integer.
	 * 
	 * @param envVar the environment variable key
	 * @return the key's value or null if the value doesn't exist or is invalid. 
	 */
	public static Integer getInteger(EnvVar envVar)
	{
		if (envVar == null) return null;
		String s = getEnvValue(envVar);
		if (s == null) return null;
		try {return Integer.valueOf(s);}
	      catch (Exception  e) {
	        _log.error(MsgUtils.getMsg("TAPIS_ENV_CONVERSION_FAILED", "Integer", s));
	    	  return null;
	      }
	}
	
	/* ---------------------------------------------------------------------------- */
	/* getLong:                                                                     */
	/* ---------------------------------------------------------------------------- */
	/** Retrieve the environment variable as a Long.
	 * 
	 * @param envVar the environment variable key
	 * @return the key's value or null if the value doesn't exist or is invalid. 
	 */
	public static Long getLong(EnvVar envVar)
	{
		if (envVar == null) return null;
		String s = getEnvValue(envVar);
		if (s == null) return null;
		try {return Long.valueOf(s);}
	      catch (Exception  e) {
	        _log.error(MsgUtils.getMsg("TAPIS_ENV_CONVERSION_FAILED", "Long", s));
	    	  return null;
	      }
	}
	
	/* ---------------------------------------------------------------------------- */
	/* getDouble:                                                                   */
	/* ---------------------------------------------------------------------------- */
	/** Retrieve the environment variable as a Double.
	 * 
	 * @param envVar the environment variable key
	 * @return the key's value or null if the value doesn't exist or is invalid. 
	 */
	public static Double getDouble(EnvVar envVar)
	{
		if (envVar == null) return null;
		String s = getEnvValue(envVar);
		if (s == null) return null;
		try {return Double.valueOf(s);}
	      catch (Exception  e) {
	        _log.error(MsgUtils.getMsg("TAPIS_ENV_CONVERSION_FAILED", "Double", s));
	    	  return null;
	      }
	}
	
	/* ---------------------------------------------------------------------------- */
	/* inEnvVarList:                                                                */
	/* ---------------------------------------------------------------------------- */
	/** Search a comma or semi-colon separated list of values assigned to the specified
	 * environment variable for the member string.  A list of one (i.e., a single value
	 * with no separator) is a valid list.
	 * 
	 * @param envVar the environment variable key
	 * @param member the string used to search the list of values assigned to the
	 * 		environment variable
	 * @return true if the member is found in the list, false otherwise
	 */
	public static boolean inEnvVarList(EnvVar envVar, String member)
	{
		// Garbage in, garbage out.
		if ((envVar == null) || (member == null)) return false;
		
		// Get the list of environment values.
		String values = get(envVar);
		if (StringUtils.isBlank(values)) return false;
		
		// Tokenize the non-empty list.
		String[] tokens = _namePattern.split(values);
		for (String name : tokens)
			if (member.equals(name)) return true;
		
		return false;
	}
	
	/* ---------------------------------------------------------------------------- */
	/* inEnvVarListPrefix:                                                          */
	/* ---------------------------------------------------------------------------- */
	/** Search a comma or semi-colon separated list of values assigned to the specified
	 * environment variable.  A match is successful if one of the list entries is a 
	 * prefix of the specified name.  A list of one (i.e., a single value with no separator) 
	 * is a valid list.
	 * 
	 * @param envVar the environment variable key
	 * @param name the url string used to match against prefixes assigned in 
	 *           the environment variable
	 * @return true if name starts with a prefix string in the list, false otherwise
	 */
	public static boolean inEnvVarListPrefix(EnvVar envVar, String name)
	{
		// Garbage in, garbage out.
		if ((envVar == null) || (name == null)) return false;
		
		// Tokenize the non-empty list.
		String[] tokens = getTokens(envVar);
		for (String prefix : tokens)
			if (name.startsWith(prefix)) return true;
		
		return false;
	}
	
	/* ---------------------------------------------------------------------------- */
	/* inEnvVarListSuffix:                                                          */
	/* ---------------------------------------------------------------------------- */
	/** Search a comma or semi-colon separated list of values assigned to the specified
	 * environment variable.  A match is successful if one of the list entries is a 
	 * suffix of the specified name.  A list of one (i.e., a single value with no separator) 
	 * is a valid list.
	 * 
	 * @param envVar the environment variable key
	 * @param name the url string used to match against suffixes assigned in 
	 *           the environment variable
	 * @return true if name ends with a suffix string in the list, false otherwise
	 */
	public static boolean inEnvVarListSuffix(EnvVar envVar, String name)
	{
		// Garbage in, garbage out.
		if ((envVar == null) || (name == null)) return false;
		
		// Tokenize the non-empty list.
		String[] tokens = getTokens(envVar);
		for (String suffix : tokens)
			if (name.endsWith(suffix)) return true;
		
		return false;
	}
	
	/* **************************************************************************** */
	/*                          Environment-Only Methods                            */
	/* **************************************************************************** */
	/* ---------------------------------------------------------------------- */
	/* getLogSecurityInfo:                                                    */
	/* ---------------------------------------------------------------------- */
	/** Go directly to the environment to get the latest security info logging
	 * value.  This effectively disregards any setting the appears in a 
	 * properties file or on the JVM command line.
	 * 
      * @return the current environment variable setting 
      */
	public static boolean getLogSecurityInfo()
	{
	    // Always return the latest environment value.
	    return getBoolean(EnvVar.TAPIS_ENVONLY_LOG_SECURITY_INFO);
	}

	/* **************************************************************************** */
	/*                               Private Methods                                */
    /* **************************************************************************** */
	/* ---------------------------------------------------------------------------- */
	/* getEnvValue:                                                                 */
	/* ---------------------------------------------------------------------------- */
	/** Retrieve the value of the environment variable checking first for
	 *  envVar.getEnvName() and then envVar.name()
	 *
	 * @param envVar the environment variable key
	 * @return the key's value as a String or null if no value has been assigned.
	 */
	private static String getEnvValue(EnvVar envVar)
	{
		if (envVar == null) return null;
		String retVal = System.getenv(envVar.getEnvName());
		if (retVal == null) retVal = System.getenv(envVar.name());
		return retVal;
	}
	
	/* ---------------------------------------------------------------------------- */
	/* getTokens:                                                                   */
	/* ---------------------------------------------------------------------------- */
	/** Get an environment variable value that represents a delimited list of strings.
	 * Tokenize the value into a string array.  This method maintains a simple cache
	 * of frequently used environment values to avoid constantly reparsing the same
	 * value.
	 * 
	 * The returned string array can be empty if the environment variable is not 
	 * assigned or empty.
	 * 
	 * @param envVar the environment variable enum
	 * @return the tokenized array of values or empty array
	 */
	private static String[] getTokens(EnvVar envVar)
	{
		// ----- Check cache
		// Return the cached tokens if they exist.
		if (envVar == EnvVar.TAPIS_REQUEST_LOGGING_FILTER_PREFIXES && 
				_loggingFilterPrefixes != null) 
			return _loggingFilterPrefixes;
		if (envVar == EnvVar.TAPIS_REQUEST_LOGGING_IGNORE_SUFFIXES && 
				_loggingFilterSuffixes != null) 
			return _loggingFilterSuffixes;
				
		// ----- Tokenize environment variable value
		// Get the list of environment values and tokenize it.
		String[] tokens;
		String values = get(envVar);
		if (StringUtils.isBlank(values)) tokens = new String[0];
		  else tokens = _namePattern.split(values);
			
		// ---- Populate cache
		// Cache frequently used tokens.  There's no need to worry about concurrent
		// writes to the fields because that JVM guarantees that addresses are written
		// atomically.  In the case of a race condition, the last writer wins, which 
		// is ok since all values are the same.  Once assigned, the fields are only read.
		if (_loggingFilterPrefixes == null && 
				envVar == EnvVar.TAPIS_REQUEST_LOGGING_FILTER_PREFIXES)
			_loggingFilterPrefixes = tokens;
		if (_loggingFilterSuffixes == null && 
				envVar == EnvVar.TAPIS_REQUEST_LOGGING_IGNORE_SUFFIXES)
			_loggingFilterSuffixes = tokens;
		
		// Return newly parsed string array.
		return tokens;
	}
}
