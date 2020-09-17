package edu.utexas.tacc.tapis.sharedapi.providers;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import edu.utexas.tacc.tapis.sharedapi.responses.TapisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This will catch any generic errors and return a 4 part Tapis response. This needs to be registered
 * in the jersey application like register(TapisExceptionMapper.class)
 *
 */
@Provider
public class TapisExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(TapisExceptionMapper.class);


    @Override
    public Response toResponse(Exception exception){
        log.error("TapisExceptionMapper : ", exception);
        TapisResponse<String> resp = TapisResponse.createErrorResponse(exception.getMessage());

         if (exception instanceof NotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(resp).build();
        } else if (exception instanceof NotAuthorizedException ) {
            return Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(resp).build();
        } else if (exception instanceof BadRequestException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(resp).build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(resp).build();
        }

    }
}