package edu.utexas.tacc.tapis.shared.utils;

import java.util.HashMap;
import java.util.Random;
import java.util.TreeMap;

import org.testng.Assert;
import org.testng.annotations.Test;

/** This file contains a base62 encoding test and a decoding test. 
 * 
 * Encoding
 * --------
 * testEncoding computes and encodes ITERATIONS number of pseudo-random 
 * byte arrays of RANDOM_LEN length.  The test then prints the distribution  
 * of the lengths of the encoded strings.
 *
 * Here are the results of encoding 500,000,000 randomly generated 16 byte 
 * arrays:
 * 
 *  length: 22, count: 182449527
 *  length: 23, count: 188420488
 *  length: 24, count: 92661622
 *  length: 25, count: 28856230
 *  length: 26, count: 6388003
 *  length: 27, count: 1067611
 *  length: 28, count: 140315
 *  length: 29, count: 14846
 *  length: 30, count: 1261
 *  length: 31, count: 88
 *  length: 32, count: 9
 *
 * In the above run, no output encoding was longer then the 32 character 
 * strings that hex encoding would generate.  Over 90% matched or beat
 * the standard base64 output length of 24 characters.   
 * 
 * Decoding
 * --------
 * testDecoding computes, encodes and decodes ITERATIONS number of 
 * pseudo-random byte arrays of RANDOM_LEN length.  The original and 
 * decoded byte arrays are compared and an exception is thrown if
 * they do not match exactly.  
 * 
 * @author rcardone
 */
@Test(groups={"unit"})
public class Base62Test 
{
    // Constants.
    private static final int RANDOM_LEN = 16;
    private static final int ITERATIONS = 100000;
    
    // Fields.
    private final Random _rand = new Random();
    private final HashMap<Integer,Integer> _lengthMap = new HashMap<>(23);
    
    /* **************************************************************************** */
    /*                                    Tests                                     */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* testEncoding:                                                                */
    /* ---------------------------------------------------------------------------- */
    @Test(enabled=true)
    public void testEncoding()
    {
        // Encoding loop.
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] bytes = new byte[RANDOM_LEN];
            _rand.nextBytes(bytes);
            String password = Base62.base62Encode(bytes);
            recordLength(password);
            // System.out.println(password);
        }
        
        // Print the encoded string lengths in sorted order.
        var sortedMap = new TreeMap<Integer,Integer>(_lengthMap);
        System.out.println("--- Base62Test.testEncoding:");
        for (var entry : sortedMap.entrySet()) {
            System.out.println("length: " + entry.getKey() + ", count: " + entry.getValue());
        }
    }
    
    /* ---------------------------------------------------------------------------- */
    /* testDecoding:                                                                */
    /* ---------------------------------------------------------------------------- */
    @Test(enabled=true)
    public void testDecoding()
    {
        // Encoding loop.
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] bytes = new byte[RANDOM_LEN];
            _rand.nextBytes(bytes);
            String password = Base62.base62Encode(bytes);
            byte[] decodeBytes = Base62.base62Decode(password);
            
            // Validate result length.
            Assert.assertEquals(decodeBytes.length, bytes.length, 
                                "Non-matching byte array length for password: " + password);
            
            // Validate result values.
            for (int j = 0; j < bytes.length; j++) 
                Assert.assertEquals(decodeBytes[j], bytes[j], 
                                    "Non-match byte value for password: " + password);
        }
    }
    
    /* **************************************************************************** */
    /*                              Private Methods                                 */
    /* **************************************************************************** */
    /* ---------------------------------------------------------------------------- */
    /* recordLength:                                                                */
    /* ---------------------------------------------------------------------------- */
    // Record the encoded string's length.
    private void recordLength(String password)
    {
        Integer len = password.length();
        var total = _lengthMap.get(len);
        if (total == null) _lengthMap.put(len, 1);
          else _lengthMap.put(len, total + 1);
    }
}
