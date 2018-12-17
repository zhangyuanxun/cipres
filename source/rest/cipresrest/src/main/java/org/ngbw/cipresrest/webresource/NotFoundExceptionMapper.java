package  org.ngbw.cipresrest.webresource;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.restdatatypes.ErrorData;
import  org.ngbw.sdk.NotFoundException;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> 
{
	private static final Log log = LogFactory.getLog(NotFoundExceptionMapper.class.getName());
	public Response toResponse(NotFoundException e)
	{
		return Response.
				status(Status.NOT_FOUND).
				type("application/xml").
				entity(new ErrorData("Not Found Error: " + e.toString(), e.getMessage(), ErrorData.NOT_FOUND)).
				build();
	}
}
