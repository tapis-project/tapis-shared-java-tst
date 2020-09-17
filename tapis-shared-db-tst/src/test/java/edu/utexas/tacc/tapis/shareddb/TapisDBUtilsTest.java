package edu.utexas.tacc.tapis.shareddb;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups={"unit"})
public class TapisDBUtilsTest 
{
    @Test
    public void escapeSqlWildcardsTest()
    {
        // No wildcards.
        String s = "abcd";
        String r = TapisDBUtils.escapeSqlWildcards(s);
        Assert.assertEquals(r, s, "Expected \"" + s + "\" = \"" + r + "\"");
       
        // % wildcards.
        s = "%ab%xy%";
        r = TapisDBUtils.escapeSqlWildcards(s);
        Assert.assertEquals(r, "\\%ab\\%xy\\%", "Expected \"" + s + "\" = \"" + r + "\"");
        
        // _ wildcards.
        s = "_ab_xy_";
        r = TapisDBUtils.escapeSqlWildcards(s);
        Assert.assertEquals(r, "\\_ab\\_xy\\_", "Expected \"" + s + "\" = \"" + r + "\"");
        
        // % wildcards.
        s = "_%ab%_%_xy%_";
        r = TapisDBUtils.escapeSqlWildcards(s);
        Assert.assertEquals(r, "\\_\\%ab\\%\\_\\%\\_xy\\%\\_", "Expected \"" + s + "\" = \"" + r + "\"");
    }
}
