package edu.utexas.tacc.tapis.sharedapi.jaxrs.filters;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.utexas.tacc.tapis.shared.TapisConstants;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadLocal;

/** This jax-rs filter clears any tapis thread local remnant that may be 
 * hanging around from a previous request.
 * 
 * @author rcardone
 */
@Provider
@Priority(TapisConstants.JAXRS_FILTER_PRIORITY_BEFORE_AUTHENTICATION)
public class ClearThreadLocalRequestFilter 
 implements ContainerRequestFilter
{
    /* ********************************************************************** */
    /*                               Constants                                */
    /* ********************************************************************** */
    // Tracing.
    private static final Logger _log = LoggerFactory.getLogger(ClearThreadLocalRequestFilter.class);
    
    // Limit logging to the first time this filter is called.
    private static volatile boolean _notLoggedYet = true;
    
    /* ********************************************************************** */
    /*                            Public Methods                              */
    /* ********************************************************************** */
    /* ---------------------------------------------------------------------- */
    /* filter:                                                                */
    /* ---------------------------------------------------------------------- */
    @Override
    public void filter(ContainerRequestContext requestContext) 
    {
        // To avoid polluting logs with repetitive information, just log the first
    	// time this filter gets executed (harmless race condition notwithstanding).
        if (_notLoggedYet && _log.isTraceEnabled()) {
            _log.trace("JAX-RX request filter ENABLED: " + this.getClass().getSimpleName() + ".");
            _notLoggedYet = false;
        }
        
        // Remove any existing tapis threadlocal information.
        TapisThreadLocal.tapisThreadContext.remove();
    }
}
