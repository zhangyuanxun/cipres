
package org.ngbw.directclient; 

import org.ngbw.restdatatypes.ErrorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * CiCipresException represents an error returned by the CIPRES REST API.
 */
@SuppressWarnings("serial")
public class CiCipresException extends Exception
{
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(CiCipresException.class.getName());
	private ErrorData ed;
	private int httpStatus;

	/**
	 * 
	 */
	/* Constructors */
	CiCipresException(ErrorData ed, int httpStatus)
	{
		this.httpStatus = httpStatus;
		this.ed = ed;
	}

	/**
	 *
	 */
	CiCipresException(Exception e, int httpStatus)
	{
		super(e);

		this.httpStatus = httpStatus;

		//log.debug("", e);
		this.ed = new ErrorData("Caught exception trying to communicate with CIPRES: " + e.toString(),
									null, ErrorData.GENERIC_COMM_ERROR);
	}

	/**
	 * Gets the error data, a structured object that includes an error code and
	 * message.  In the case of form validation errors, may also include a collection
	 * of field error messages.
	 *
	 * @return the error data
	 */
	public ErrorData getErrorData() { return this.ed; }

	public int getHttpStatus() {return this.httpStatus; }
}
