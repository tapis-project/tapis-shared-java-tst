package edu.utexas.tacc.tapis.shareddb;

public final class TapisDBUtils 
{
    /** Create the aloe database url with the given parameters.
     * 
     * @param host db host
     * @param port db port
     * @param database schema name
     * @return the jdbc url
     */
    public static String makeJdbcUrl(String host, int port, String database) 
    {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
    
    /** SQL queries on string fields can contain wildcard characters (% and _).
     * This method escapes those characters to avoid having them interpreted 
     * by SQL as wildcards.
     * 
     * @param s the string that might contain wildcards
     * @return the string with wildcards escaped
     */
    public static String escapeSqlWildcards(String s)
    {
        s = s.replace("%", "\\%");
        s = s.replace("_", "\\_");
        return s;
    }
}
