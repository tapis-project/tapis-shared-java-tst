package edu.utexas.tacc.tapis.shareddb.migrate;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.shared.exceptions.TapisJDBCException;
import edu.utexas.tacc.tapis.shared.i18n.MsgUtils;
import edu.utexas.tacc.tapis.shareddb.datasource.HikariDSGenerator;;

/** This is a simple container class for parsed input values.
 * 
 * @author rich
 */
public class TapisJDBCMigrateParms 
{
 /* **************************************************************************** */
 /*                                 Constants                                    */
 /* **************************************************************************** */
 // Tracing.
 private static final Logger _log = LoggerFactory.getLogger(TapisJDBCMigrateParms.class);
    
 // Default password for the service's runtime, non-admin user (tapis).
 private static final String DFT_TAPIS_USER_PASSWORD = "password"; // change on 1st use  
    
 /* **************************************************************************** */
 /*                                  Fields                                      */
 /* **************************************************************************** */
  @Option(name = "-h", required = true, aliases = {"-host"}, 
      metaVar = "<name>", usage = "database host (IP address or DNS name)")
  public String host;

  @Option(name = "-u", required = true, aliases = {"-user"}, 
      metaVar = "<name>", usage = "database admin user name")
  public String username;

  @Option(name = "-pw", required = true, aliases = {"-password"}, 
      metaVar = "<string>", usage = "database admin user's password")
  public String password;

  @Option(name = "-tpw", required = false, aliases = {"-tapisPassword"}, 
          metaVar = "<string>", usage = "tapis user's password")
      public String tapisPassword = DFT_TAPIS_USER_PASSWORD;

  @Option(name = "-cmddir", required = false, aliases = {"-cmddirectory"}, 
	  metaVar = "<string>", usage = "Directory of SQL command files as specifiedy by Flyway")
  public String cmdDirectory = "edu/utexas/tacc/tapis/migrate/sql,classpath:edu/utexas/tacc/tapis/migrate/scripts";
  
  @Option(name = "-dbmsname", required = false,
	  metaVar = "<name>", usage = "db management system name (ex: postgresql)")
  public String dbmsName = "postgresql";

  @Option(name = "-driver", required = false, aliases = {"-driverclass"}, 
	  metaVar = "<string>", usage = "fully qualified name of class implementing IJdbcDriver interface")
  public String driverClass; 
	  
  @Option(name = "-p", required = false, aliases = {"-port"}, 
      metaVar = "<num>", usage = "database port number")
  public int port = 5432;

  @Option(name = "-schema", required = false, 
      metaVar = "<name>", usage = "database schema name")
  public String schema = HikariDSGenerator.TAPIS_SCHEMA_NAME;

  @Option(name = "-C", required = false, aliases = {"-CLEANDB"}, 
      usage = "Clean but don't drop the existing database, causing a full redeployment",
      forbids = {"-CONLY", "-D", "-DONLY", "-BASELINE"})
  public boolean isCleanDatabases;

  @Option(name = "-CONLY", required = false, aliases = {"-CLEANONLY"}, 
      usage = "Clean but don't drop the existing database and then exit",
      forbids = {"-C", "-D", "-DONLY", "-BASELINE"})
  public boolean isCleanOnly;
  
  @Option(name = "-D", required = false, aliases = {"-DROPDB"}, 
      usage = "Drop the existing database, causing a full redeployment",
      forbids = {"-DONLY", "-C", "-CONLY", "-BASELINE"})
  public boolean isDropDatabases;

  @Option(name = "-DONLY", required = false, aliases = {"-DROPONLY"}, 
      usage = "Drop the existing database and then exit",
      forbids = {"-D", "-C", "-CONLY", "-BASELINE"})
  public boolean isDropOnly;

  @Option(name = "-BASELINE", required = false,  
	      usage = "Baeline an existing database and then exit",
	      forbids = {"-D", "-DONLY", "-C", "-CONLY"})
  public boolean doBaseline = false;

  @Option(name = "-help", aliases = {"--help"}, 
      usage = "display help information")
  public boolean help;

 /* **************************************************************************** */
 /*                               Constructors                                   */
 /* **************************************************************************** */
 /** Constructor for command line arguments.
  * 
  * @param args command line arguments.
  */
 public TapisJDBCMigrateParms(String[] args)
     throws TapisJDBCException
 {
  initializeParms(args);
  validateParms();
 }
  
 /* **************************************************************************** */
 /*                               Private Methods                                */
 /* **************************************************************************** */
 /* ---------------------------------------------------------------------------- */
 /* initializeParms:                                                             */
 /* ---------------------------------------------------------------------------- */
 /** Parse the input arguments. */
 protected void initializeParms(String[] args)
     throws TapisJDBCException
 {
  // Get a command line parser to verify input.
  CmdLineParser parser = new CmdLineParser(this);
  parser.getProperties().withUsageWidth(120);
  
  try {
     // Parse the arguments.
     parser.parseArgument(args);
    }
   catch (CmdLineException e)
    {
     if (!help)
       {
        // Create message buffer of sufficient size.
        final int initialCapacity = 1024;
        StringWriter writer = new StringWriter(initialCapacity);
        
        // Write parser error message.
        writer.write("\n******* Input Parameter Error *******\n");
        writer.write(e.getMessage());
        writer.write("\n\n");
        
        // Write usage information--unfortunately we need an output stream.
        writer.write("TapisJDBCMigrate [options...]\n");
        ByteArrayOutputStream ostream = new ByteArrayOutputStream(initialCapacity);
        parser.printUsage(ostream);
        try {writer.write(ostream.toString(Charset.defaultCharset().toString()));}
          catch (Exception e1) {}
        writer.write("\n");
        
        // Throw exception.
        throw new TapisJDBCException(writer.toString());
       }
    }
  
  // Display help and exit program.
  if (help)
    {
     String s = "\nTapisJDBCMigrate creates and/or migrates databases.";
     System.out.println(s);
     System.out.println("\nTapisJDBCMigrate [options...] tablename\n");
     parser.printUsage(System.out);
     System.exit(0);
    }
 }
 
 /* ---------------------------------------------------------------------------- */
 /* validateParms:                                                               */
 /* ---------------------------------------------------------------------------- */
 /** Validate the input arguments. */
 protected void validateParms()
     throws TapisJDBCException
 {
     // Make sure the parms with default values are still assigned.
     if (StringUtils.isBlank(tapisPassword)) {
         String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateParms", "tapisPassword");
         _log.error(msg);
         throw new TapisJDBCException(msg);
     }
     if (StringUtils.isBlank(cmdDirectory)) {
         String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateParms", "cmdDirectory");
         _log.error(msg);
         throw new TapisJDBCException(msg);
     }
     if (StringUtils.isBlank(dbmsName)) {
         String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateParms", "dbmsName");
         _log.error(msg);
         throw new TapisJDBCException(msg);
     }
     if (StringUtils.isBlank(schema)) {
         String msg = MsgUtils.getMsg("TAPIS_NULL_PARAMETER", "validateParms", "schema");
         _log.error(msg);
         throw new TapisJDBCException(msg);
     }
 }
}
