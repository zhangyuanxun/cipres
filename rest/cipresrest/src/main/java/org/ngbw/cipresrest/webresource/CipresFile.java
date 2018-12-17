/*
 * CipresFile.java
 */
package org.ngbw.cipresrest.webresource;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Deque;
import java.util.LinkedList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.ngbw.cipresrest.auth.AuthHelper;
import org.ngbw.restdatatypes.ErrorData;
import org.ngbw.restdatatypes.FolderItemInfo;
import org.ngbw.sdk.ValidationException;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * REST methods:
 *
 * The {@literal <identifier>} in the following methods refers to either a UUID or
 * a pathname. Pathnames can be either absolute or relative; relative pathnames
 * are considered to be relative to the user's home folder. Special characters in
 * pathnames, like the folder separator character, can be escaped with backslashes.
 *
 * POST /v1/file/{@literal <identifier>}
 *   Imports a file. The <code>identifier</code> parameter indicates the destination
 *   folder for the file. File data can be given in the post with the <code>fileData</code>
 *   parameter, or its location can be indicated with the <code>sourceUri</code> parameter.
 *   If the value of <code>sourceUri</code> corresponds to the local TUS server, the
 *   uploaded file will be imported. The optional <code>fileName</code> parameter is
 *   used to explicitly set the name of the imported file.
 *
 * GET /v1/file/{@literal <identifier>}
 *   Retrieves a folder item. If the item is a data item, the response is the item's
 *   data. Otherwise, the response is detailed information about the item.
 *
 * PUT /v1/file/{@literal <identifier>}
 *   Modifies the file system. The type of modification is determined by the value
 *   of the <code>action</code> parameter: <code>move</code> moves the indicated
 *   folder item to the location given in the <code>fileName</code> parameter.
 *   <code>mkdir</code> creates a new directory.
 *
 * DELETE /v1/file/{@literal <identifier>}
 *   Deletes a folder item.
 *
 * GET /v1/file/list/{@literal <identifier>}
 *   Retrieves detailed information about a folder item.
 *
 * @author Paul Hoover
 *
 */
@Path("/v1/file")
public class CipresFile {

	// nested classes


	/**
	 *
	 */
	private abstract class TargetItem<T extends FolderItem> {

		// data fields


		protected final T m_item;
		protected String m_path;


		// constructors


		public TargetItem(T item)
		{
			this(item, null);
		}

		public TargetItem(T item, String path)
		{
			m_item = item;

			setPath(path);
		}


		// public methods



		public void setLabel(String label)
		{
			m_item.setLabel(label);
		}

		public void setEnclosingFolder(Folder enclosingFolder)
		{
			m_item.setEnclosingFolder(enclosingFolder);
		}

		public void setPath(String path)
		{
			m_path = path;
		}

		public void save() throws IOException, SQLException
		{
			m_item.save();
		}

		public void delete() throws IOException, SQLException
		{
			m_item.delete();
		}

		public FolderItemInfo createInfo() throws IOException, SQLException
		{
			if (m_path == null)
				m_path = buildFolderPath(m_item);

			return createInfo(m_path);
		}

		public FolderItemInfo createDetailedInfo() throws IOException, SQLException
		{
			if (m_path == null)
				m_path = buildFolderPath(m_item);

			return createDetailedInfo(m_path);
		}

		public abstract Response buildResponse() throws IOException, SQLException;


		// protected methods


		protected abstract FolderItemInfo createInfo(String path) throws IOException, SQLException;

		protected abstract FolderItemInfo createDetailedInfo(String path) throws IOException, SQLException;
	}

	/**
	 *
	 */
	private class FolderTarget extends TargetItem<Folder> {

		// constructors


		public FolderTarget(Folder folder)
		{
			super(folder);
		}

		public FolderTarget(Folder folder, String path)
		{
			super(folder, path);
		}


		// public methods


		@Override
		public Response buildResponse() throws IOException, SQLException
		{
			FolderItemInfo info = createDetailedInfo();
			ResponseBuilder response = Response.ok(info);

			response.type(MediaType.APPLICATION_XML);

			return response.build();
		}


		// protected methods


		@Override
		protected FolderItemInfo createInfo(String path) throws IOException, SQLException
		{
			return Info.createFolderInfo(m_uriInfo, m_item, path, false);
		}

		@Override
		protected FolderItemInfo createDetailedInfo(String path) throws IOException, SQLException
		{
			return Info.createFolderInfo(m_uriInfo, m_item, path, true);
		}
	}

	/**
	 *
	 */
	private class TaskTarget extends TargetItem<Task> {

		// constructors


		public TaskTarget(Task task)
		{
			super(task);
		}

		public TaskTarget(Task task, String path)
		{
			super(task, path);
		}


		// public methods


		@Override
		public Response buildResponse() throws IOException, SQLException
		{
			FolderItemInfo info = createDetailedInfo();
			ResponseBuilder response = Response.ok(info);

			response.type(MediaType.APPLICATION_XML);

			return response.build();
		}


		// protected methods


		@Override
		protected FolderItemInfo createInfo(String path) throws IOException, SQLException
		{
			return Info.createTaskInfo(m_uriInfo, m_item, path, false);
		}

		@Override
		protected FolderItemInfo createDetailedInfo(String path) throws IOException, SQLException
		{
			return Info.createTaskInfo(m_uriInfo, m_item, path, true);
		}
	}

	/**
	 *
	 */
	private class DataItemTarget extends TargetItem <UserDataItem> {

		// constructors


		public DataItemTarget(UserDataItem item)
		{
			super(item);
		}

		public DataItemTarget(UserDataItem item, String path)
		{
			super(item, path);
		}


		// public methods


		@Override
		public Response buildResponse() throws IOException, SQLException
		{
			ResponseBuilder response = Response.ok(m_item.getDataAsStream());

			response.header("Content-Disposition", "attachment; filename=" + m_item.getLabel());
			response.type(MediaType.APPLICATION_OCTET_STREAM);

			return response.build();
		}


		// protected methods


		@Override
		protected FolderItemInfo createInfo(String path) throws IOException, SQLException
		{
			return Info.createFileInfo(m_uriInfo, m_item, path);
		}

		@Override
		protected FolderItemInfo createDetailedInfo(String path) throws IOException, SQLException
		{
			return Info.createFileInfo(m_uriInfo, m_item, path);
		}
	}


	// data fields


	private @Context ContainerRequestContext m_requestContext;
	private @Context UriInfo m_uriInfo;


	// public methods


	@POST
	@Path("{identifier: .+}")
	@Produces(MediaType.APPLICATION_XML)
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public FolderItemInfo writeFile(@PathParam("identifier") String identifier, FormDataMultiPart formData) throws IOException, SQLException, URISyntaxException, CipresException
	{
		String folderName;
		Folder destFolder;

		if (identifier.matches("[0-9a-fA-F]+")) {
			destFolder = Folder.findFolderByUUID(identifier);

			if (destFolder != null) {
				String path = buildFolderPath(destFolder);

				folderName = path + FolderItem.SEPARATOR + destFolder.getLabel();
			}
			else
				folderName = null;
		}
		else {
			if (identifier.startsWith(FolderItem.SEPARATOR))
				folderName = identifier;
			else {
				User owner = getAuthenticatedUser();

				folderName = FolderItem.SEPARATOR + owner.getUsername() + FolderItem.SEPARATOR + identifier;
			}

			destFolder = Folder.findFolder(folderName);
		}

		if (destFolder == null)
			throw new CipresException("Folder " + identifier + " not found", Status.NOT_FOUND, ErrorData.NOT_FOUND);

		FormDataBodyPart fileNamePart = formData.getField("fileName");
		String fileName;

		if (fileNamePart != null)
			fileName = fileNamePart.getValue();
		else
			fileName = null;

		FormDataBodyPart sourceUriPart;
		FormDataBodyPart fileDataPart;
		UserDataItem newItem;

		if ((sourceUriPart = formData.getField("sourceUri")) != null) {
			String sourceUriValue = sourceUriPart.getValue();
			URI sourceUri = new URI(sourceUriValue);
			String scheme = sourceUri.getScheme();

			if (scheme == null || scheme.equalsIgnoreCase("file"))
				newItem = new UserDataItem(sourceUri.getPath(), destFolder);
			else if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
				ThisApplication app = ThisApplication.getInstance();
				URL tusUrl = app.getTusURL();

				if (sourceUri.getHost().equalsIgnoreCase(tusUrl.getHost()) && sourceUri.getPort() == tusUrl.getPort() && sourceUri.getPath().startsWith(tusUrl.getPath())) {
					String uuid = sourceUri.getPath().substring(tusUrl.getPath().length() + 1);
					String infoFileName = app.getTusStorage() + File.separator + uuid + ".info";
					ObjectMapper mapper = new ObjectMapper();
					JsonNode infoRoot = mapper.readTree(infoFileName);
					JsonNode node = infoRoot.get("username");

					if (node == null || !node.isTextual())
						throw new CipresException("Unexpected TUS file info format", Status.INTERNAL_SERVER_ERROR, ErrorData.GENERIC_SERVICE_ERROR);

					User owner = getAuthenticatedUser();

					if (!node.textValue().equals(owner.getUsername()))
						throw new CipresException("Authenticated user doesn't own uploaded file", Status.FORBIDDEN, ErrorData.AUTHORIZATION);

					if (fileNamePart == null) {
						node = infoRoot.get("suggestedFilename");

						if (node != null && node.isTextual())
							fileName = node.textValue().trim();
					}

					String binFileName = app.getTusStorage() + File.separator + uuid + ".bin";

					newItem = new UserDataItem(binFileName, destFolder);

					newItem.setUUID(uuid);
				}
				else {
					InputStream inStream = sourceUri.toURL().openStream();

					newItem = new UserDataItem(destFolder);

					newItem.setData(inStream);
				}
			}
			else if (scheme.equalsIgnoreCase("ftp")) {
				InputStream inStream = sourceUri.toURL().openStream();

				newItem = new UserDataItem(destFolder);

				newItem.setData(inStream);
			}
			else
				throw new ValidationException("sourceUri", "unsupported scheme " + scheme);

			if (fileName == null) {
				int offset = sourceUriValue.lastIndexOf(FolderItem.SEPARATOR);

				if (offset >= 0)
					fileName = sourceUriValue.substring(offset + 1);
				else
					fileName = sourceUriValue;
			}
		}
		else if ((fileDataPart = formData.getField("fileData")) != null) {
			if (fileNamePart == null)
				fileName = fileDataPart.getContentDisposition().getFileName();

			newItem = new UserDataItem(destFolder);

			newItem.setData(fileDataPart.getEntityAs(InputStream.class));
		}
		else
			throw new CipresException("No file data", Status.BAD_REQUEST, ErrorData.BAD_REQUEST);

		newItem.setLabel(fileName);
		newItem.save();

		return Info.createFileInfo(m_uriInfo, newItem, folderName);
	}

	@GET
	@Path("{identifier: .+}")
	public Response getFile(@PathParam("identifier") String identifier) throws IOException, SQLException, CipresException
	{
		TargetItem<?> target = findTarget(identifier);

		if (target == null)
			throw new CipresException(identifier + " not found", Status.NOT_FOUND, ErrorData.NOT_FOUND);

		return target.buildResponse();
	}

	@PUT
	@Path("{identifier: .+}")
	@Produces(MediaType.APPLICATION_XML)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public FolderItemInfo updateFile(@PathParam("identifier") String identifier, MultivaluedMap<String, String> formData) throws IOException, SQLException, CipresException
	{
		String action;

		if (formData == null || (action = formData.getFirst("action")) == null)
			throw new ValidationException("action", "missing");

		if (action.equalsIgnoreCase("move")) {
			String newPathName = formData.getFirst("fileName");

			if (newPathName == null)
				throw new ValidationException("fileName", "missing");

			if (!newPathName.startsWith(FolderItem.SEPARATOR)) {
				User owner = getAuthenticatedUser();

				newPathName = FolderItem.SEPARATOR + owner.getUsername() + FolderItem.SEPARATOR + newPathName;
			}

			int offset = newPathName.lastIndexOf(FolderItem.SEPARATOR);
			String newFolderName = newPathName.substring(0, offset);
			String newFileName = newPathName.substring(offset + 1);
			Folder newFolder = Folder.findFolder(newFolderName);

			if (newFolder == null)
				throw new CipresException(newFolderName + " not found", Status.NOT_FOUND, ErrorData.NOT_FOUND);

			TargetItem<?> target = findTarget(identifier);

			if (target == null)
				throw new CipresException(identifier + " not found", Status.NOT_FOUND, ErrorData.NOT_FOUND);

			target.setPath(newFolderName);
			target.setLabel(newFileName);
			target.setEnclosingFolder(newFolder);
			target.save();

			return target.createInfo();
		}
		else if (action.equalsIgnoreCase("mkdir")) {
			if (!identifier.startsWith(FolderItem.SEPARATOR)) {
				User owner = getAuthenticatedUser();

				identifier = FolderItem.SEPARATOR + owner.getUsername() + FolderItem.SEPARATOR + identifier;
			}

			int offset = identifier.lastIndexOf(FolderItem.SEPARATOR);
			String baseName;

			if (offset > 0)
				baseName = identifier.substring(0, offset);
			else
				baseName = FolderItem.SEPARATOR;

			User owner = getAuthenticatedUser();
			Folder newFolder = Folder.findOrCreateFolder(owner, identifier);

			return Info.createFolderInfo(m_uriInfo, newFolder, baseName, false);
		}
		else
			throw new ValidationException("action", "unrecognized value " + action);
	}

	@DELETE
	@Path("{identifier: .+}")
	public void deleteFile(@PathParam("identifier") String identifier) throws IOException, SQLException, CipresException
	{
		TargetItem<?> target = findTarget(identifier);

		if (target == null)
			throw new CipresException(identifier + " not found", Status.NOT_FOUND, ErrorData.NOT_FOUND);

		target.delete();
	}

	@GET
	@Path("list/{identifier: .+}")
	@Produces(MediaType.APPLICATION_XML)
	public FolderItemInfo getListing(@PathParam("identifier") String identifier) throws IOException, SQLException, CipresException
	{
		TargetItem<?> target = findTarget(identifier);

		if (target == null)
			throw new CipresException(identifier + " not found", Status.NOT_FOUND, ErrorData.NOT_FOUND);

		return target.createDetailedInfo();
	}


	// private methods


	private String buildFolderPath(FolderItem item) throws IOException, SQLException
	{
		Deque<String> labels = new LinkedList<String>();
		Folder enclosingFolder = item.getEnclosingFolder();

		while (enclosingFolder != null) {
			labels.push(enclosingFolder.getLabel());

			enclosingFolder = enclosingFolder.getEnclosingFolder();
		}

		StringBuilder builder = new StringBuilder();

		for (String label : labels) {
			builder.append(FolderItem.SEPARATOR);
			builder.append(label);
		}

		return builder.toString();
	}

	private User getAuthenticatedUser()
	{
		return (User)m_requestContext.getProperty(AuthHelper.USER);
	}

	private TargetItem<?> findTarget(String identifier) throws IOException, SQLException, CipresException
	{
		if (identifier.matches("NGBW-JOB-[0-9a-zA-Z_]+-[0-9a-fA-F]+")) {
			Task targetTask = Task.findTaskByJobHandle(identifier);

			if (targetTask != null)
				return new TaskTarget(targetTask);
		}
		else if (identifier.matches("[0-9a-fA-F]+")) {
			Folder targetFolder = Folder.findFolderByUUID(identifier);

			if (targetFolder != null)
				return new FolderTarget(targetFolder);

			UserDataItem targetItem = UserDataItem.findDataItemByUUID(identifier);

			if (targetItem != null)
				return new DataItemTarget(targetItem);
		}
		else {
			if (!identifier.startsWith(FolderItem.SEPARATOR)) {
				User owner = getAuthenticatedUser();

				identifier = FolderItem.SEPARATOR + owner.getUsername() + FolderItem.SEPARATOR + identifier;
			}

			int offset = identifier.lastIndexOf(FolderItem.SEPARATOR);
			String path;

			if (offset > 0)
				path = identifier.substring(0, offset);
			else
				path = FolderItem.SEPARATOR;

			Folder targetFolder = Folder.findFolder(identifier);

			if (targetFolder != null)
				return new FolderTarget(targetFolder, path);

			UserDataItem targetItem = UserDataItem.findDataItem(identifier);

			if (targetItem != null)
				return new DataItemTarget(targetItem, path);

			Task targetTask = Task.findTask(identifier);

			if (targetTask != null)
				return new TaskTarget(targetTask, path);
		}

		return null;
	}
}
