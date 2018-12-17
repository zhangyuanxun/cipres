package org.ngbw.cipresrest.webresource;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;

import java.util.ArrayList;
import java.util.List;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.sdk.common.util.FileUtils;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.jobs.Job;
import org.ngbw.sdk.ValidationException;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.api.tool.FieldError;



/*
*/
public class JobFormParser
{
	private static final Log log = LogFactory.getLog(JobFormParser.class.getName());
	private FormDataMultiPart formData;

	private String toolId = "";
	private String taskLabel = "";
	private String folderName = "";

	// LinkedHashMap preserves insertion order.
	private Map<String, String> metadata = new LinkedHashMap<String, String>();
	private Map<String, List<String>> visibleParameters = new LinkedHashMap<String, List<String>>();
	private Map<String, List<UserDataItem>> inputFiles = new LinkedHashMap<String, List<UserDataItem>>();

	private User user;

	private List<FieldError> errors = new ArrayList<FieldError>();

	/*
		Todo: note that our generated jsp pages, from
		cipres-portal/src/main/codeGeneration/FreeMarkerTemplates/pise2JSP[Simple|Advanced].ftl
		allow 600. Database, tool_parameters table, allows 100 chars for name, 1024 for value.

		The purpose of these limits is mostly to make sure we aren't dealing with a malicious
		request or a client's coding error that could cause us to store a ridiculous amount
		of garbage.

		TODO: all I'm checking now are max_fields, name_len and value_len.
	*/
	private static int MAX_VALUE_LEN = 600;
	private static int MAX_NAME_LEN = 100;
	private static int MAX_METADATA= 100;
	private static int MAX_INPUT_FILES = 100;
	private static int MAX_VPARAMS = 2000;
	private static int MAX_FIELDS = MAX_INPUT_FILES + MAX_VPARAMS + MAX_METADATA + 1;

	public JobFormParser(User user)
	{
		this.user = user;
	}

	public String getToolId()
	{
		return this.toolId;
	}

	public Map<String, String> getMetadata()
	{
		return this.metadata;
	}

	public Map<String, List<String>> getVisibleParameters()
	{
		return this.visibleParameters;
	}

	public Map<String, List<UserDataItem>> getInputFiles()
	{
		return this.inputFiles;
	}

	public String getFolderName()
	{
		return this.folderName;
	}

	public String getTaskLabel()
	{
		return this.taskLabel;
	}


	/*
		Tries to accumulate as many  errors as it can find so user doesn't
		have to get them one at a time.  However, if the request is just too large
		or some other 'fatal' error, this *throws* a ValidationException instead
		of returning a list of errors.  I'm treating these as fatal as
		it seems likely that the request is complete garbage in these cases.
	*/
	public List<FieldError> parse(FormDataMultiPart formData)
	{
		int fieldCount = 0;
		this.formData = formData;

		Map<String, List<FormDataBodyPart>> retval = new HashMap<String, List<FormDataBodyPart>>();
		List<UserDataItem> uploaded = new ArrayList<UserDataItem>();
		for(String name:  formData.getFields().keySet())
		{
			fieldCount++;
			if (fieldCount > MAX_FIELDS)
			{
				throw new ValidationException("Too many fields.");
			}
			log.debug("Process: " + name);
			String[] nameParts = name.split("\\.");
			if (nameParts.length < 2)
			{
				if (name.equals("tool"))
				{
					toolId = getStringField("tool");
					if ((toolId == null) || toolId.length() == 0)
					{
						log.debug("tool value is invalid");
						errors.add(new FieldError("tool", "Is required."));
					}
					// otherwise toolId is fine.
				} else if (name.equals("folderName"))
				{
					folderName = getStringField("folderName");
				} else if (name.equals("taskName"))
				{
					taskLabel = getStringField("taskName");
				} else
				{
					errors.add(new FieldError(name, "Is not a valid field name."));
				}
				continue;
			}

			List<FormDataBodyPart> parts = formData.getFields(name);
			if (parts == null)
			{
				log.debug("body of param " + name + " is null");
				errors.add(new FieldError(name, "Invalid value."));
				continue;
			}

			String prefix = nameParts[0];
			String fname = nameParts[1];
			if (fname.length() < 1 || fname.length() > MAX_NAME_LEN)
			{
				throw new ValidationException("Invalid Field name. Length, after the prefix, must be between 1 and " + MAX_NAME_LEN + " characters." );
			}
			if (prefix.equals("metadata"))
			{
				String value = getStringField(name);
				if (value == null)
				{
					log.debug("value of param " + name + " is null");
					errors.add(new FieldError(fname, "Invalid value."));
				} else
				{
					log.debug("add " + fname + "=" + value + " to metadata");
					metadata.put(fname, value);
				}
			} else if (prefix.equals("input"))
			{
				List<UserDataItem> value;
				if (nameParts.length > 2 && nameParts[2].equalsIgnoreCase("url"))
				{
					value = getInputFilesFromUrl(name);
				} else
				{
					value = getInputFiles(name);

					uploaded.addAll(value);
				}
				if (value == null)
				{
					log.debug("null value for input param");
					errors.add(new FieldError(fname, "Invalid file value."));
				} else
				{
					log.debug("add " + fname + " to inputFiles");
					List<UserDataItem> files = inputFiles.get(fname);
					if (files == null)
						inputFiles.put(fname, value);
					else {
						for (UserDataItem item : value)
							files.add(item);
					}
				}
			} else if (prefix.equals("vparam"))
			{
				List<String> value = getListOfStringField(name);
				if (value == null)
				{
					log.debug("value of " + name + " is null");
					errors.add(new FieldError(fname, "Invalid value."));
				} else
				{
					log.debug("add " + fname + "=" + value + " to visible parameters");
					visibleParameters.put(fname, value);
				}
			} else
			{
				log.debug( "Invalid field name.");
				errors.add(new FieldError(name, "Invalid field name. Missing prefix."));
			}
		}

		if (!uploaded.isEmpty() && !folderName.isEmpty()) {
			try {
				Folder enclosingFolder = Folder.findOrCreateFolder(user, folderName);

				for (UserDataItem item : uploaded) {
					item.setEnclosingFolder(enclosingFolder);
					item.save();
				}
			}
			catch (IOException | SQLException err) {
				log.error("", err);
			}
		}

		return errors;
	}

	String getStringField(String name)
	{
		List<String> l = getListOfStringField(name);
		if (l == null)
		{
			return null;
		}
		if (l.size() > 1)
		{
			errors.add(new FieldError(name, "List value not allowed"));
		}
		return l.get(0);
	}

	/*
		Returns white space trimmed field values if present.  Null values are omitted from the list
		but empty strings are kept.  Returns null if there are no values in the list.
		Adds error if value is too long or wrong type.
	*/
	List<String> getListOfStringField(String name)
	{
		List<String> values = new ArrayList<String>();

		String value;
		for (FormDataBodyPart part : formData.getFields(name))
		{
			value = null;
			if (part != null)
			{
				if (part.isSimple())
				{
					value = part.getValue();
					if (value == null)
					{
						log.debug("1");
						errors.add(new FieldError(name, "Null value not allowed"));
						continue;
					}
				} else
				{
					log.debug("2");
					errors.add(new FieldError(name, "Invalid field value type"));
					continue;
				}
			} else
			{
				log.debug("3");
				errors.add(new FieldError(name, "Null value not allowed"));
				continue;
			}
			if (value.length() > MAX_VALUE_LEN)
			{
				errors.add(new FieldError(name, "Field too long.  Max is " + MAX_VALUE_LEN));
				continue;
			}
			value = value.trim();
			values.add(value);
		}
		return (values.size() == 0 ? null : values);
	}

	List<UserDataItem> getInputFilesFromUrl(String fieldName)
	{
		try {
			List<FormDataBodyPart> bodyParts = formData.getFields(fieldName);
			List<UserDataItem> result = new ArrayList<UserDataItem>();

			for (FormDataBodyPart part : bodyParts) {
				URL fileUrl = new URL(part.getValue());
				URL restUrl = new URL(Workbench.getInstance().getProperties().getProperty("rest.url"));

				if (fileUrl.getHost().equalsIgnoreCase(restUrl.getHost()) && fileUrl.getPort() == restUrl.getPort() && fileUrl.getPath().startsWith(restUrl.getPath())) {
					int offset = restUrl.getPath().length();

					if (!restUrl.getPath().endsWith("/"))
						offset += 1;

					String subPath = fileUrl.getPath().substring(offset);

					if (!subPath.startsWith("file/"))
						throw new ValidationException("URL must reference the file resource");

					String pathParam = subPath.substring(5);
					UserDataItem item;

					if (pathParam.matches("[0-9a-fA-F]+"))
						item = UserDataItem.findDataItemByUUID(pathParam);
					else
						item = UserDataItem.findDataItem(pathParam);

					result.add(item);
				}
				else
					throw new ValidationException("URL must point to " + restUrl);
			}

			return result;
		}
		catch (MalformedURLException | ValidationException err) {
			errors.add(new FieldError(fieldName, err.getMessage()));

			return null;
		}
		catch (IOException | SQLException err) {
			log.error("", err);

			throw new RuntimeException("error finding file data for input parameter " + fieldName);
		}
	}

	List<UserDataItem> getInputFiles(String fieldName)
	{
		List<FormDataBodyPart> bodyParts = formData.getFields(fieldName);
		List<UserDataItem> result = new ArrayList<UserDataItem>();

		for (FormDataBodyPart part : bodyParts) {
			UserDataItem udi = null;
			ContentDisposition cd = part.getContentDisposition();
			String filename = cd.getFileName();
			log.debug("Filename for parameter " + fieldName + " is '" + filename + "'.");

			InputStream is = null;
			try
			{
				is = part.getValueAs(InputStream.class);

				// put the file data in the cipres db.
				udi = Job.createUserDataItem(user, is, filename);
			}
			catch (Exception e)
			{
				if (is == null)
				{
					errors.add(new FieldError(fieldName, "value must be of input file type."));
				} else
				{
					log.error("", e);
					throw new RuntimeException("error reading or storing file data for input parameter " + fieldName);
				}
			}
			finally
			{
				if (is != null) { try { is.close(); } catch(Exception e) { } }
			}
			result.add(udi);
		}

		return result;
	}
}
