package org.ngbw.web.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.apache.log4j.Logger;

import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.core.shared.TaskRunStage;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.Task;
import org.ngbw.sdk.database.TaskInputSourceDocument;
import org.ngbw.sdk.database.TaskLogMessage;
import org.ngbw.sdk.database.TaskOutputSourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.util.TaskSortableField;
import org.ngbw.sdk.api.tool.FileHandler;
import org.ngbw.web.model.Page;
import org.ngbw.web.model.Tab;
import org.ngbw.web.model.TabbedPanel;
import org.ngbw.web.model.impl.ListPage;

/**
 * Struts action class to manage user tasks in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class ManageTasks extends DataManager
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(ManageTasks.class.getName());
	// session attribute key constants
	public static final String CURRENT_TASK = "currentTask";
	public static final String FOLDER_TASKS = "folderTasks";
	public static final String TASK_FOLDER = "taskFolder";
	public static final String INPUT_MAP = "inputMap";

	// result constants
	public static final String DISPLAY_INPUT = "displayInput";
	public static final String DISPLAY_PARAMETERS = "displayParameters";
	public static final String DISPLAY_OUTPUT_LIST = "displayOutputList";
	public static final String DISPLAY_WORKING_DIRECTORY = "displayWorkingDirectory";

	// task tab properties
	public static final String TASK_PAGE_SIZE = "taskPageSize";

	// task action constants
	public static final String MOVE = "Move";
	public static final String COPY = "Copy";
	public static final String DELETE = "Delete Selected";

	// task stage constants
	public static final String READY = "READY";
	public static final String COMPLETED = "COMPLETED";

	/*================================================================
	 * Properties
	 *================================================================*/
	// task list form properties
	private String taskAction;

	// task list page properties
	private Task inputTask;
	private Task outputTask;
	private Map<String, List<TaskInputSourceDocument>> input;
	private Map<String, List<TaskOutputSourceDocument>> output;

	/*================================================================
	 * Action methods
	 *================================================================*/
	@SkipValidation
	public String list() {
		Folder folder = getRequestFolder(ID);
		if (folder == null)
			folder = getCurrentFolder();
		if (folder == null) {
			reportUserError("You must select a folder to view its tasks.");
			return HOME;
		} else {
			if (isCurrentFolder(folder) == false)
				setCurrentFolder(folder);
			TabbedPanel<Task> tabs = getFolderTaskTabs();
			String tab = getRequestParameter(TAB);
			if (tab != null)
				tabs.setCurrentTab(tab);
			return LIST;
		}
	}

	@SkipValidation
	public String refresh() {
		refreshFolderTaskTabs();
		return LIST;
	}

	@SuppressWarnings("unchecked")
	@SkipValidation
	public String display() {
		// get selected task ID from request param, if present
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0) {
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
			return DISPLAY;
		} else if (getCurrentTask() != null) {
			return DISPLAY;
		} else {
			addActionError("You must select a task to view its details.");
			return LIST;
		}
	}

	@SkipValidation
	public String displayInput() {
		// get selected task ID from request param, if present
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0) {
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
			return DISPLAY_INPUT;
		} else if (getCurrentTask() != null) {
			return DISPLAY_INPUT;
		} else {
			addActionError("You must select a task to view its input.");
			return LIST;
		}
	}

	@SkipValidation
	public String displayWorkingDirectory() {
		// get selected task ID from request param, if present
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0) {
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
			return DISPLAY_WORKING_DIRECTORY;
		} else if (getCurrentTask() != null) {
			return DISPLAY_WORKING_DIRECTORY;
		} else {
			addActionError("You must select a task to view its working directory.");
			return LIST;
		}
	}


	@SkipValidation
	public String displayParameters() {
		// get selected task ID from request param, if present
		String[] taskId = (String[])getParameters().get(ID);
		if (taskId != null && taskId.length > 0) {
			setCurrentTask(getSelectedTask(Long.parseLong(taskId[0])));
			return DISPLAY_PARAMETERS;
		} else if (getCurrentTask() != null) {
			return DISPLAY_PARAMETERS;
		} else {
			addActionError("You must select a task to view its parameters.");
			return LIST;
		}
	}

	@SuppressWarnings("unchecked")
	@SkipValidation
	public String delete() {
		// delete the current task
		try {
			Task currentTask = getCurrentTask();
			if (currentTask == null) {
				reportUserError("You must select a task to delete it.");
			} else {
				String taskLabel = currentTask.getLabel();
				if (deleteTask(currentTask)) {
					setCurrentTask(null);
					reportUserMessage("Task \"" + taskLabel + "\" successfully deleted.");
					refreshFolderTaskTabs();
				} else {
					reportUserError("Task \"" + taskLabel + "\" could not be deleted.");
					return DISPLAY;
				}
			}
			return LIST;
		} catch (Throwable error) {
			reportError(error, "Error deleting task");
			return ERROR;
		}
	}

	@SkipValidation
	public String cancel() {
		String taskAction = getTaskAction();
		if (taskAction == null) {
			addActionError("You must select an action to manipulate your tasks.");
		} else {
			Long[] selectedIds = getSelectedIds();
			if (selectedIds == null || selectedIds.length < 1) {
				addActionError("You must select one or more tasks to " +
					taskAction.toLowerCase() + " them.");
			} else {
				/**/
				String[] button = (String[])getParameters().get("method:cancel");
				if (button != null && button.length > 0 && button[0].equals(DELETE))
					return deleteSelected();
				/**/
				Folder folder = getCurrentFolder();
				Long targetFolder = getTargetFolder();
				if (targetFolder == null) {
					addActionError("You must select a target folder to " +
						taskAction.toLowerCase() + " your tasks.");
				} else if (targetFolder.equals(folder.getFolderId())) {
					addActionError("You must select a new folder to " +
						taskAction.toLowerCase() + " your tasks into.");
				} else if (taskAction.equalsIgnoreCase(MOVE)) {
					int moved = moveSelectedTasks();
					String result = moved +
					" task";
					if (moved != 1) result += "s were ";
					else result += " was ";
					result += "successfully moved.";
					reportUserMessage(result);
					refreshFolderTaskTabs();
				} else {
					addActionError("You have requested an unrecognized task action, " +
						"please select from the list below.");
				}
			}
		}
		return LIST;
	}

	@SkipValidation
	public String cancelSelected() {
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1) {
			addActionError("You must select one or more tasks to cancel them.");
		} else {
			int cancelled = cancelSelectedTasks();
			int remaining = selectedIds.length - cancelled;
			if (cancelled > 0) {
				String result = cancelled + " task";
				if (cancelled != 1) result += "s were ";
				else result += " was ";
				result += "successfully cancelled.";
				reportUserMessage(result);
				refreshFolderTaskTabs();
			}
			if (remaining > 0) {
				String result = remaining + " task";
				if (remaining != 1) result += "s";
				result += " could not be cancelled.";
				reportUserError(result);
			}
		}
		return LIST;
	}

	@SkipValidation
	public String deleteSelected() {
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1) {
			addActionError("You must select one or more tasks to delete them.");
		} else {
			int deleted = deleteSelectedTasks();
			int remaining = selectedIds.length - deleted;
			if (deleted > 0) {
				String result = deleted + " task";
				if (deleted != 1) result += "s were ";
				else result += " was ";
				result += "successfully deleted.";
				reportUserMessage(result);
				refreshFolderTaskTabs();
			}
			if (remaining > 0) {
				String result = remaining + " task";
				if (remaining != 1) result += "s";
				result += " could not be deleted.";
				reportUserError(result);
			}
		}
		return LIST;
	}

	/*================================================================
	 * Task list form property accessor methods
	 *================================================================*/
	public String getTaskAction() {
		return taskAction;
	}

	public void setTaskAction(String taskAction) {
		this.taskAction = taskAction;
	}

	/*================================================================
	 * Task list page property accessor methods
	 *================================================================*/
	@SuppressWarnings("unchecked")
	public TabbedPanel<Task> getFolderTaskTabs() {
		TabbedPanel<Task> folderTasks =
			(TabbedPanel<Task>)getSession().get(FOLDER_TASKS);
		if (folderTasks == null ||
			folderTasks.isParentFolder(getCurrentFolder()) == false)
			folderTasks = refreshFolderTaskTabs();
		return folderTasks;
	}

	@SuppressWarnings("unchecked")
	public void setFolderTaskTabs(TabbedPanel<Task> folderTasks) {
		getSession().put(FOLDER_TASKS, folderTasks);
	}

	public boolean hasFolderTasks() {
		TabbedPanel<Task> folderTasks = getFolderTaskTabs();
		return (folderTasks != null && folderTasks.getTabs().getTotalNumberOfElements() > 0);
	}

	public List<String> getTabLabels() {
		TabbedPanel<Task> folderTasks = getFolderTaskTabs();
		if (folderTasks == null)
			return null;
		else return folderTasks.getTabLabels();
	}

	public String getFirstTabLabel() {
		TabbedPanel<Task> folderTasks = getFolderTaskTabs();
		if (folderTasks == null)
			return null;
		else return folderTasks.getFirstTabLabel();
	}

	public String getCurrentTabLabel() {
		TabbedPanel<Task> folderTasks = getFolderTaskTabs();
		if (folderTasks == null)
			return null;
		else {
			Tab<Task> currentTab = folderTasks.getCurrentTab();
			if (currentTab == null)
				return null;
			else return currentTab.getLabel();
		}
	}

	public int getCurrentTabSize() {
		TabbedPanel<Task> folderTasks = getFolderTaskTabs();
		if (folderTasks == null)
			return 0;
		else return folderTasks.getCurrentTabSize();
	}

	//TODO: add sort functionality to user interface
	public List<Task> getCurrentTaskTab() {
		TabbedPanel<Task> folderTasks = getFolderTaskTabs();
		if (folderTasks == null)
			return null;
		else return folderTasks.getCurrentTabContents();
	}

	public String getLabel(Task task) {
		if (task == null)
			return null;
		else return truncateText(task.getLabel());
	}

	public String getJobHandle(Task task) {
		if (task == null)
			return null;
		else return truncateText(task.getJobHandle());
	}

	public String getOwner(Task task) {
		if (task == null)
			return null;
		else try {
			return task.getUser().getUsername();
		} catch (Throwable error) {
			reportError(error, "Error retrieving username of user who owns task " +
				task.getTaskId());
			return null;
		}
	}

	public String getGroup(Task task) {
		if (task == null)
			return null;
		else try {
			return task.getGroup().getGroupname();
		} catch (Throwable error) {
			reportError(error, "Error retrieving group name of group that owns task " +
				task.getTaskId());
			return null;
		}
	}

	public String getCreationDate(Task task) {
		if (task == null)
			return null;
		else try {
			return formatDate(task.getCreationDate());
		} catch (Throwable error) {
			reportError(error, "Error retrieving creation date of task " +
				task.getTaskId());
			return null;
		}
	}

	public String getToolLabel(Task task) {
		if (task == null)
			return null;
		else return getToolLabel(task.getToolId());
	}

	public boolean isNew(Task task) {
		if (task == null)
			return false;
		else {
			TaskRunStage stage = task.getStage();
			if (stage == null)
				return false;
			else return stage.equals(TaskRunStage.NEW);
		}
	}

	public boolean isReady(Task task) {
		if (task == null)
			return false;
		else {
			TaskRunStage stage = task.getStage();
			if (stage == null)
				return false;
			else return stage.equals(TaskRunStage.READY);
		}
	}

	public boolean isCompleted(Task task) {
		if (task == null)
			return false;
		else {
			TaskRunStage stage = task.getStage();
			if (stage == null)
				return false;
			else return stage.equals(TaskRunStage.COMPLETED);
		}
	}

	public boolean hasError(Task task) {
		if (task == null)
			return false;
		else return (task.isOk() == false);
	}

	public Folder getTaskFolder() {
		return (Folder)getSessionAttribute(TASK_FOLDER);
	}

	public void setTaskFolder(Folder folder) {
		setSessionAttribute(TASK_FOLDER, folder);
	}

	public Task getNewTask() {
		Folder folder = getCurrentFolder();
		Folder taskFolder = getTaskFolder();
		if (folder == null || taskFolder == null ||
			folder.getFolderId() != taskFolder.getFolderId())
			return null;
		else {
			// only return current task if it's transient,
			// otherwise it's not an editable new task
			Task task = getCurrentTask();
			if (task.isNew())
				return task;
			else return null;
		}
	}

	public boolean hasNewTask() {
		return (getNewTask() != null);
	}

	/*================================================================
	 * Task input, output and parameter property accessor methods
	 *================================================================*/
	@SuppressWarnings("unchecked")
	public Map<String, List<TaskInputSourceDocument>> getInputMap() {
		return (Map<String, List<TaskInputSourceDocument>>)getSessionAttribute(INPUT_MAP);
	}

	public void setInputMap(Map<String, List<TaskInputSourceDocument>> inputMap) {
		setSessionAttribute(INPUT_MAP, inputMap);
	}

	/*
	 * Returns all "file" parameters, including the main input parameter
	 */
	public Map<String, List<TaskInputSourceDocument>> getInput(Task task) {
		try {
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			// first try the task input stored in the action
			else if (input != null && inputTask != null &&
				inputTask.equals(task))
				return input;
			// if not found, retrieve it from the workbench
			else {
				WorkbenchSession session = getWorkbenchSession();
				if (session == null)
					throw new NullPointerException("No session is present.");
				else {
					input = session.getInput(task);
					inputTask = task;
					return input;
				}
			}
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving input from selected task");
			return null;
		}
	}

	/*
	 * Returns only the main input parameter
	 */
	public List<TaskInputSourceDocument> getMainInput(Task task) {
		if (task == null)
			return null;
		else {
			String inputParam = getMainInputParameter(task.getToolId());
			if (inputParam == null)
				return null;
			else {
				Map<String, List<TaskInputSourceDocument>> input = getInput(task);
				if (input != null && input.containsKey(inputParam))
					return input.get(inputParam);
				else return null;
			}
		}
	}

	public boolean hasMainInput(Task task) {
		return (getMainInput(task) != null);
	}

	public int getMainInputCount(Task task) {
		List<TaskInputSourceDocument> mainInput = getMainInput(task);
		if (mainInput == null)
			return 0;
		else return mainInput.size();
	}

	/*
	 * Returns all "file" parameters other than the main input parameter
	 */
	public Map<String, List<TaskInputSourceDocument>> getParameterInput(Task task) {
		Map<String, List<TaskInputSourceDocument>> input = getInput(task);
		if (input == null)
			return null;
		else {
			String inputParam = getMainInputParameter(task.getToolId());
			if (inputParam != null && input.containsKey(inputParam)) {
				input = new HashMap<String, List<TaskInputSourceDocument>>(input);
				input.remove(inputParam);
			}
			return input;
		}
	}

	/*
	 * Returns true if any parameters are set other than the main input parameter. This
	 * includes primitive (String) parameters, as well as "file" (SourceDocument) parameters.
	 *
	 * Algorithm:
	 * 1. If there are any primitive parameters, return true
	 * 2. If there are no file parameters, return false
	 * 3. If there is more than one file parameter, return true (since only one can be the main input)
	 * 4. If the one file parameter is the main input, return false
	 * 5. Return true
	 */
	public boolean hasParameters(Task task) {
		if (task == null)
			return false;
		else {
			Map<String, String> parameters = null;
			try {
				parameters = task.toolParameters();
			} catch (Throwable error) {
				reportError(error, "Error retrieving parameters for task \"" +
					task.getLabel() + "\"");
				return false;
			}
			if (parameters == null || parameters.size() < 1) {
				Map<String, List<TaskInputSourceDocument>> input = getInput(task);
				if (input == null || input.size() < 1)
					return false;
				else if (input.size() == 1){
					String inputParam = getMainInputParameter(task.getToolId());
					if (inputParam != null && input.containsKey(inputParam))
						return false;
					else return true;
				} else return true;
			} else return true;
		}
	}

	/*
	 * Returns the number of set parameters both primitive and "file",
	 * not including the main input parameter
	 */
	public int getParameterCount(Task task) {
		if (task == null)
			return 0;
		else {
			int parameterCount = 0;
			Map<String, String> parameters = null;
			try {
				parameters = task.toolParameters();
			} catch (Throwable error) {
				reportError(error, "Error retrieving parameters for task \"" +
					task.getLabel() + "\"");
				return 0;
			}
			if (parameters != null)
				parameterCount += parameters.size();
			Map<String, List<TaskInputSourceDocument>> parameterInput = getParameterInput(task);
			if (parameterInput != null)
				parameterCount += parameterInput.size();
			return parameterCount;
		}
	}

	public Map<String, List<TaskOutputSourceDocument>> getOutput(Task task) {
		try {
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			// first try the task output stored in the action
			else if (output != null && outputTask != null &&
				outputTask.equals(task))
				return output;
			// if not found, retrieve it from the workbench
			else {
				WorkbenchSession session = getWorkbenchSession();
				if (session == null)
					throw new NullPointerException("No session is present.");
				else {
					output = session.getOutput(task);
					outputTask = task;
					return output;
				}
			}
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving output from selected task");
			return null;
		}
	}

	public boolean hasOutput(Task task) {
		Map<String, List<TaskOutputSourceDocument>> output = getOutput(task);
		if (output != null && output.size() > 0)
			return true;
		else return false;
	}

	public int getOutputCount(Task task) {
		Map<String, List<TaskOutputSourceDocument>> output = getOutput(task);
		if (output == null)
			return 0;
		else return output.size();
	}

	public boolean hasWorkingDirectory(Task task) throws Exception
	{
		return getWorkbenchSession().workingDirectoryExists(task);
	}

	public List<FileHandler.FileAttributes> getWorkingDirectoryList(Task task)
	{
		try
		{
			return getWorkbenchSession().listWorkingDirectory(task);
		}
		catch (Throwable error)
		{
			reportCaughtError(error, "Error getting list of working directory files for selected task.");
			return null;
		}
	}

	/*================================================================
	 * Task display page property accessor methods
	 *================================================================*/
	public Task getCurrentTask() {
		return (Task)getSessionAttribute(CURRENT_TASK);
	}

	public void setCurrentTask(Task task) {
		setSessionAttribute(CURRENT_TASK, task);
	}

	public boolean isRefreshable() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return (isNew(task) == false && isReady(task) == false &&
				isCompleted(task) == false && task.isOk());
		} catch (Throwable error) {
			reportCaughtError(error, "Error determining whether current task is refreshable");
			return false;
		}
	}

	public Long getCurrentTaskId() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return task.getTaskId();
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving database ID of current task");
			return null;
		}
	}

	public String getCurrentTaskLabel() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getLabel(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving label of current task");
			return null;
		}
	}

	public String getCurrentTaskOwner() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getOwner(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving owner of current task");
			return null;
		}
	}

	public String getCurrentTaskGroup() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getGroup(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving group of current task");
			return null;
		}
	}

	public String getCurrentTaskCreationDate() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getCreationDate(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving creation date of current task");
			return null;
		}
	}

	public String getTool() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return task.getToolId();
		} catch (Throwable error) {
			// This seems to happen so often I'm not sure there's any reason to log it.
			//reportCaughtError(error, "Error retrieving tool of current task");
			return null;
		}
	}

	public String getToolLabel() {
		return getToolLabel(getTool());
	}

	public String getToolAction() {
		String tool = getTool();
		if (tool == null || tool.trim().equals(""))
			return null;
		else return tool.toLowerCase();
	}

	public String getMainInputParameter() {
		return getMainInputParameter(getTool());
	}

	public boolean hasMainInput() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return hasMainInput(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error determining whether current task has main input");
			return false;
		}
	}

	public List<TaskInputSourceDocument> getMainInput() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getMainInput(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's main input");
			return null;
		}
	}

	public int getMainInputCount() {
		List<TaskInputSourceDocument> mainInput = getMainInput();
		if (mainInput == null)
			return 0;
		else return mainInput.size();
	}

	public boolean hasParameters() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return hasParameters(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error determining whether current task has parameters");
			return false;
		}
	}

	public Map<String, String> getSimpleParameters() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return task.toolParameters();
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's simple parameters");
			return null;
		}
	}

	public Map<String, List<TaskInputSourceDocument>> getParameterInput() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getParameterInput(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's file parameters");
			return null;
		}
	}

	public int getParameterCount() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getParameterCount(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's parameter count");
			return 0;
		}
	}

	public boolean hasOutput() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return hasOutput(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error determining whether current task has output");
			return false;
		}
	}

	public boolean hasWorkingDirectory()
	{
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return hasWorkingDirectory(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error determining whether current task has working directory");
			return false;
		}
	}

	public List<FileHandler.FileAttributes> getWorkingDirectoryList()
	{
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getWorkingDirectoryList(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error getting list of files in working directory of current task.");
			return null;
		}
	}


	public Map<String, List<TaskOutputSourceDocument>> getOutput() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getOutput(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's output");
			return null;
		}
	}

	public int getOutputCount() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return getOutputCount(task);
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's output count");
			return 0;
		}
	}

	public String getCurrentTaskStage() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else {
				TaskRunStage stage = task.getStage();
				if (stage == null || stage.toString() == null)
					throw new NullPointerException("Current task has no stage.");
				else return stage.toString();
			}
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving execution stage of current task");
			return null;
		}
	}

	public boolean hasTaskMessages() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else {
				List<TaskLogMessage> messages = task.logMessages();
				return (messages != null && messages.size() > 0);
			}
		} catch (Throwable error) {
			reportCaughtError(error, "Error determining whether current task has messages");
			return false;
		}
	}

	public List<TaskLogMessage> getTaskMessages() {
		try {
			Task task = getCurrentTask();
			if (task == null)
				throw new NullPointerException("No task is currently selected.");
			else return task.logMessages();
		} catch (Throwable error) {
			reportCaughtError(error, "Error retrieving current task's messages");
			return null;
		}
	}

	/*================================================================
	 * Page methods
	 *================================================================*/
	public Page<? extends FolderItem> getCurrentPage() {
		return getFolderTaskTabs().getCurrentTab().getContents();
	}

	@SuppressWarnings("unchecked")
	public void setPageSize(Integer pageSize) {
		try {
			getController().
				setUserPreference(TASK_PAGE_SIZE, pageSize.toString());
			TabbedPanel<Task> taskPanel = getFolderTaskTabs();
			if (taskPanel != null && pageSize != null) {
				for (Tab<Task> taskTab : taskPanel.getTabs().getAllElements()) {
					List<Task> taskList =
						taskTab.getContents().getAllElements();
					taskTab.setContents(
						new ListPage<Task>(taskList, pageSize));
				}
			}
		} catch (Throwable error) {
			reportError(error, "Error saving user's selected task page size");
		}
		getCurrentPage().setPageSize(pageSize);
	}

	/*================================================================
	 * Internal property accessor methods
	 *================================================================*/
	protected Task getSelectedTask(Long taskId) {
		try {
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else return session.findTask(taskId);
		} catch (Throwable error) {
			reportError(error, "Error retrieving selected task");
			return null;
		}
	}

	public TabbedPanel<Task> refreshFolderTaskTabs() {
		try {
			Folder folder = getCurrentFolder();
			TabbedPanel<Task> folderTasks = null;
			if (folder == null)
				throw new NullPointerException("No folder is currently selected.");
			else {
				//TODO: Add support for multiple task tab categories
				List<Task> taskList = folder.findTasks();
				getWorkbenchSession().sortTasks(taskList, TaskSortableField.ID, true);
				if (taskList != null && taskList.size() > 0) {
					folderTasks = new TabbedPanel<Task>(folder);
					List<Tab<Task>> taskTabs = new Vector<Tab<Task>>(1);
					Page<Task> taskPage = new ListPage<Task>(taskList);
					Tab<Task> taskTab = new Tab<Task>(taskPage, "Tasks");
					taskTabs.add(taskTab);
					folderTasks.setTabs(new ListPage<Tab<Task>>(taskTabs));
					folderTasks.sortTabs();
				}
				setFolderTaskTabs(folderTasks);
				refreshFolders();
				return folderTasks;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving tabbed folder tasks");
			return null;
		}
	}

	protected void cancelTask(Task task) {
		try {
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else if (task == null)
				throw new NullPointerException("Task was not found.");
			else {
				session.cancelTask(task);
			}
		} catch (Throwable error) {
			reportError(error, "Error cancelling task");
		}
	}

	protected boolean deleteTask(Task task) {
		try {
			WorkbenchSession session = getWorkbenchSession();
			if (session == null)
				throw new NullPointerException("No session is present.");
			else if (task == null)
				throw new NullPointerException("Task was not found.");
			else {
				Long id = task.getTaskId();
				session.deleteTask(task);
				task = session.findTask(id);
				if (task == null)
					return true;
				else return false;
			}
		} catch (Throwable error) {
			reportError(error, "Error deleting task");
			return false;
		}
	}

	protected String getMainInputParameter(String tool) {
		if (tool == null)
			return null;
		else {
			String inputParam = getConceptLabel("ToolInput", tool);
			if (inputParam != null && inputParam.equals(tool))
				return null;
			else return inputParam;
		}
	}

	// BIG_FILES ###
	public String getData(SourceDocument document)
	{
		try
		{
			return getSourceDataAsString(document);
		}
		catch (Throwable error)
		{
			reportError(error, "Error retrieving data from source document");
			return null;
		}
	}

	protected int moveSelectedTasks() {
		// get IDs of selected tasks to move
		int moved = 0;
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return moved;

		// get target folder to move to
		Folder folder = getFolderController().getFolder(getTargetFolder());
		if (folder == null)
			addActionError("Error moving task to folder with ID " +
				getTargetFolder() + ": folder not found.");

		// move selected tasks to target folder
		else for (int i=0; i<selectedIds.length; i++) {
			Task task = getSelectedTask(selectedIds[i]);
			if (task == null)
				addActionError("Error moving task with ID " +
					selectedIds[i] + ": item not found.");
			else try {
				getWorkbenchSession().move(task, folder);
				moved++;
			} catch (Throwable error) {
				reportError(error, "Error moving task \"" + task.getLabel() +
					"\" to folder \"" + folder.getLabel() + "\"");
			}
		}
		return moved;
	}

	/**
	 * Updated by Mona to refresh user data size after deletion.
	 */
	protected int deleteSelectedTasks() {
		int deleted = 0;
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return deleted;
		else {
			User user = null;

			for (int i=0; i<selectedIds.length; i++) {
				Task task = getSelectedTask(selectedIds[i]);
				if (task == null)
					addActionError("Error deleting task with ID " +
							selectedIds[i] + ": item not found.");
				else {
					try {
						if (deleteTask(task)) {
							user = task.getUser();
							deleted++;
						}
					} catch (Throwable error) {
						reportError(error, "Error deleting task \"" +
								task.getLabel() + "\"");
					}
				}
			}

			if ( user != null )
			{
				clearActionErrors();
				refreshUserDataSize ( user );
			}
		}
		return deleted;
	}

	protected int cancelSelectedTasks() {
		int cancelled = 0;
		Long[] selectedIds = getSelectedIds();
		if (selectedIds == null || selectedIds.length < 1)
			return cancelled;
		else {
			for (int i=0; i<selectedIds.length; i++) {
				Task task = getSelectedTask(selectedIds[i]);
				if (task == null)
					addActionError("Error cancelling task with ID " +
							selectedIds[i] + ": item not found.");
				else {
					try {
						cancelTask(task);

						cancelled++;
					} catch (Throwable error) {
						reportError(error, "Error cancelling task \"" +
								task.getLabel() + "\"");
					}
				}
			}
		}
		return cancelled;
	}
}
