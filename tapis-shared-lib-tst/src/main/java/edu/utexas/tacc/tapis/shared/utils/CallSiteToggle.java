package edu.utexas.tacc.tapis.shared.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/** This class can be used to turn on and off individual call sites by providing
 * a theadsafe toggle switch that can be turned on and off.  The methods that change the
 * position of the switch, toggleOn and toggleOff, return true only when the switch 
 * transitions between states.  For example, if toggleOn is called when the switch is
 * already on, then no transition occurs, the switch remains on, and false is returned.  
 * The testing and state transition of the toggle take place atomically.
 * 
 * The motivating use case is to control whether logging takes place at a particular site
 * by adjusting this toggle on error and non-error paths through a method.  The goal is
 * to minimize redundant log records that fill up log files when an error condition persists
 * across multiple calls.  Logging is turned off when the error condition is first detected 
 * and only turned back on when the condition clears. 
 * 
 * @author rcardone
 */
public final class CallSiteToggle 
{
    // Thread-safe toggle switch initially set to ON (i.e., allow logging).
    private final AtomicBoolean _toggleSwitch = new AtomicBoolean(true);
    
    /** Set the limit toggle switch to off only if it is currently onf.
     * True is returned only if the switch changes value, that is, on transitions
     * from on to off.  The toggle is always off after this call completes.
     * 
     * @return true if the toggle was on and was reset to off.
     */
    public boolean toggleOff() {return _toggleSwitch.compareAndSet(true, false);}
    
    /** Set the limit toggle switch to on only if it is currently off.  
     * True is returned only if the switch changes value, that is, on transitions
     * from off to on.  The toggle is always on after this call completes.
     * 
     * @return true if the toggle was off and was reset to on.
     */
    public boolean toggleOn() {return _toggleSwitch.compareAndSet(false, true);}
}
