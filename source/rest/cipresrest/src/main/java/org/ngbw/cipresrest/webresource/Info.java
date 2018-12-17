/*
 * Info.java
 */
package org.ngbw.cipresrest.webresource;


import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ngbw.restdatatypes.DataItemInfo;
import org.ngbw.restdatatypes.FolderInfo;
import org.ngbw.restdatatypes.FolderItemInfo;
import org.ngbw.restdatatypes.TaskFileInfo;
import org.ngbw.restdatatypes.TaskInfo;
import org.ngbw.restdatatypes.TaskMessageInfo;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.NotExistException;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.TaskLogMessage;
import org.ngbw.sdk.database.TaskOutputSourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;


/**
 *
 * @author Paul Hoover
 *
 */
class Info {

	// data fields


	private static final Log m_log = LogFactory.getLog(Info.class.getName());


	// public methods


	/**
	 *
	 * @param uriInfo
	 * @param item
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public static DataItemInfo createFileInfo(UriInfo uriInfo, UserDataItem item, String path) throws IOException, SQLException
	{
		DataItemInfo result = new DataItemInfo();

		setFolderItemFields(uriInfo, result, item, path);

		result.length = item.getDataLength();

		return result;
	}

	/**
	 *
	 * @param uriInfo
	 * @param task
	 * @param path
	 * @param detailed
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public static TaskInfo createTaskInfo(UriInfo uriInfo, Task task, String path, boolean detailed) throws IOException, SQLException
	{
		TaskInfo result = new TaskInfo();

		setFolderItemFields(uriInfo, result, task, path);

		result.tool = task.getToolId();
		result.handle = task.getJobHandle();
		result.stage = task.getStage().toString();
		result.failed = !task.isOk();

		if (detailed) {
			User owner = task.getUser();
			UriBuilder builder = uriInfo.getBaseUriBuilder();
			URI linkBase = builder.path(CipresRoot.class).path("job").path(owner.getUsername()).path(task.getJobHandle()).build();
			String inputLinkBase = linkBase.toString() + "/input/";
			String outputLinkBase = linkBase.toString() + "/output/";

			result.inputs = new ArrayList<TaskFileInfo>();
			result.outputs = new ArrayList<TaskFileInfo>();
			result.messages = new ArrayList<TaskMessageInfo>();

			for (Map.Entry<String, List<TaskInputSourceDocument>> entry : task.input().entrySet()) {
				String param = entry.getKey();
				List<TaskInputSourceDocument> inputDocs = entry.getValue();

				for (TaskInputSourceDocument doc : inputDocs) {
					String fileName;

					try {
						String docName = doc.getName();
						UserDataItem inputItem = new UserDataItem(Long.parseLong(docName));

						fileName = inputItem.getLabel();
					}
					catch (NumberFormatException | NotExistException err) {
						m_log.debug(err.getMessage());

						fileName = doc.getName();
					}

					TaskFileInfo info = new TaskFileInfo();

					info.id = doc.getInputDocumentId();
					info.length = doc.getDataLength();
					info.name = fileName;
					info.parameter = param;
					info.link.url = inputLinkBase + String.valueOf(info.id);
					info.dateCreated = task.getCreationDate();

					result.inputs.add(info);
				}
			}

			for (Map.Entry<String, List<TaskOutputSourceDocument>> entry : task.output().entrySet()) {
				String param = entry.getKey();
				List<TaskOutputSourceDocument> outputDocs = entry.getValue();

				for (TaskOutputSourceDocument doc : outputDocs) {
					TaskFileInfo info = new TaskFileInfo();

					info.id = doc.getOutputDocumentId();
					info.length = doc.getDataLength();
					info.name = doc.getName();
					info.parameter = param;
					info.link.url = outputLinkBase + String.valueOf(info.id);
					info.dateCreated = task.getCreationDate();

					result.outputs.add(info);
				}
			}

			for (TaskLogMessage message : task.logMessages()) {
				TaskMessageInfo info = new TaskMessageInfo();

				info.text = message.getMessage();
				info.stage = message.getStage().toString();
				info.timestamp = message.getCreationDate();

				result.messages.add(info);
			}
		}

		return result;
	}

	/**
	 *
	 * @param uriInfo
	 * @param folder
	 * @param path
	 * @param detailed
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	public static FolderInfo createFolderInfo(UriInfo uriInfo, Folder folder, String path, boolean detailed) throws IOException, SQLException
	{
		FolderInfo result = new FolderInfo();

		setFolderItemFields(uriInfo, result, folder, path);

		if (detailed) {
			result.dataItems = new ArrayList<DataItemInfo>();
			result.folders = new ArrayList<FolderInfo>();
			result.tasks = new ArrayList<TaskInfo>();

			String basePath = path;

			if (!path.endsWith(FolderItem.SEPARATOR))
				basePath += FolderItem.SEPARATOR;

			basePath += result.label;

			for (UserDataItem item : folder.findDataItems()) {
				DataItemInfo info = createFileInfo(uriInfo, item, basePath);

				result.dataItems.add(info);
			}

			for (Folder subFolder : folder.findSubFolders()) {
				FolderInfo info = createFolderInfo(uriInfo, subFolder, basePath, false);

				result.folders.add(info);
			}

			for (Task task : folder.findTasks()) {
				TaskInfo info = createTaskInfo(uriInfo, task, basePath, false);

				result.tasks.add(info);
			}
		}

		return result;
	}


	// private methods


	private static void setFolderItemFields(UriInfo uriInfo, FolderItemInfo info, FolderItem item, String path) throws IOException, SQLException
	{
		UriBuilder builder = uriInfo.getBaseUriBuilder();
		URI link = builder.path(CipresFile.class).path(item.getUUID()).build();

		if (path.endsWith(FolderItem.SEPARATOR))
			path = path.substring(0, path.length() - 1);

		info.uuid = item.getUUID();
		info.path = path;
		info.label = item.getLabel();
		info.owner = item.getUser().getUsername();
		info.link.url = link.toString();
		info.dateCreated = item.getCreationDate();
	}
}
