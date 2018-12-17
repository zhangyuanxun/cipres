
package org.ngbw.cipresrest.webresource;

import java.util.List;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
//import javax.activation.MimetypesFileTypeMap;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

public class BaseResource
{
	@SuppressWarnings("unused")
	private static final Log log = LogFactory.getLog(BaseResource.class.getName());
	@Context UriInfo uriInfo;

	/*
		Todo: add types like .aln, .dnd, .fasta, .nex, etc as text/plain.
		Is there a way to say the default is text/plain?
	*/
	String getMimeType(String filename)
	{

		// This is always returning application/octet-stream, at least on my mac.  I guess
		// I would need to create a map in one of the locations the documentation indicates.
		//String mimeType = mimeTypesMap.getContentType(filename);

		String mimeType = "text/plain";
		return mimeType;
	}


	public JobUriBuilder getJobUriBuilder(String jobHandle, String username)
	{
		return new JobUriBuilder(uriInfo, jobHandle, username);
	}


	/*
		BIG problem with this method is that isSimple() is returned for all fields
		so field.getValue() reads entire files into memory.
	*/
	public String multiPartFormDataAsString(FormDataMultiPart formData, int maxlen)
	{
		String retval = "";
		String value;
		for(String name:  formData.getFields().keySet())
		{
			List<FormDataBodyPart> fields = formData.getFields(name);
			for (FormDataBodyPart field : fields)
			{
				ContentDisposition cd = field.getContentDisposition();
				// hopefully all file parameters have non null filename,  though I"m not sure if that's true.
				if ((cd != null) && cd.getFileName() != null)
				{
					// just print the filename not its contents.
					retval += (name + "=" + cd.getFileName());
				} else
				{
					if (field.isSimple())
					{
						if (maxlen > 0)
						{
							// this call to getValue() reads whole field into memory.
							// There's no way to check the length beforehand as far as I can tell.
							int len = field.getValue().length();
							retval += (name + "=" + field.getValue().substring(0, Math.min(maxlen, len)));
							if (len > maxlen)
							{
								retval += "...(" + len + ")";
							}
						} else
						{
							retval += (name + "=" + field.getValue());
						}
					} else
					{
						retval += (name + " is not a simple field type.");
					}
				}
				retval +="\n";
			}
		}
		return retval;
	}

}


