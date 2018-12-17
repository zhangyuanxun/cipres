package org.ngbw.web.actions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.controllers.FolderController;
import org.ngbw.sdk.DataDetails;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;

/*
	10/2012 Terri Liebowitz Schwartz

	This code, as originally written, reads the whole file into memory which really limits the size of files
	we can upload. It also assumes the file is text and tries to guess the character encoding and inserts
	CR/NLs, which of course means we can't upload binary files.

	I'm going to assume files are either binary or ascii text (no fancy character set encodings) and avoid
	reading the whole file into memory at once.  If I remember, my changes for this will be marked with "BIG_FILES".
*/

/**
 * Struts action class to handle creation (upload/edit/search)
 * of user data in the NGBW web application.
 *
 * @author Jeremy Carver
 */
@SuppressWarnings("serial")
public class CreateData extends DataManager
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(CreateData.class.getName());
	// session attribute key constants
	public static final String UPLOADED_FILES = "uploadedFiles";

	// file upload result values
	public static final String UPLOAD_STATUS = "uploadStatus";
	public static final String UPLOAD_SUCCESS = "SUCCESS";
	public static final String UPLOAD_ERROR = "ERROR: No uploaded files were found.";
	public static final String UPLOAD_ERROR_PREFIX = "ERROR: ";

	// data upload preference keys
	public static final String UPLOAD_ENTITY_TYPE = "Upload_Entity_Type";
	public static final String UPLOAD_DATA_TYPE = "Upload_Data_Type";
	public static final String UPLOAD_DATA_FORMAT = "Upload_Data_Format";

	/*================================================================
	 * Properties
	 *================================================================*/
	// data item upload/edit form properties
	private UserDataItem dataItem;
	private String entityType;
	private String dataType;
	private String dataFormat;

	// Pasted data form control is "source"
	private String source = null;
	private String sourceLabel = null;

	// Uploaded file info is stored here, by the form.
	private File[] uploads;
	private String[] uploadFileNames;

	/*================================================================
	 * Action methods
	 *================================================================*/

	 public void setSourceLabel(String s) { sourceLabel = s; }
	 public String getSourceLabel() { return sourceLabel; }




	@SkipValidation
	public String upload() {
		logger.debug("In upload");
		// clear current data item and data form, since this is a new data item
		clearCurrentData();
		clearUploadedFiles();

		// pre-populate last selected upload SourceDocumentType
		Folder currentFolder = getCurrentFolder();
		FolderController controller = getFolderController();
		setEntityType(controller.getFolderPreference(currentFolder, UPLOAD_ENTITY_TYPE));
		setDataType(controller.getFolderPreference(currentFolder, UPLOAD_DATA_TYPE));
		setDataFormat(controller.getFolderPreference(currentFolder, UPLOAD_DATA_FORMAT));
		return INPUT;
	}


	public String executeUpload() {
		logger.debug("In executeUpload");
		if (validateUpload())
			return execute();
		else return INPUT;
	}

	public String executePaste() {
		logger.debug("In executePaste");
		if (validatePaste())
			return execute();
		else return INPUT;
	}

	@SkipValidation
	public String edit() {
		UserDataItem currentData = getCurrentData();
		if (currentData != null) {
			setDataItem(currentData);
			return INPUT;
		} else {
			addActionError("You must select a data item to edit its details.");
			return LIST;
		}
	}

	public String execute() 
	{
		try 
		{
			int saved = saveDataItems();
			if (saved < 1) 
			{
				reportUserError("No data items were saved.");
				return INPUT;
			} else 
			{
				String message = saved + ((saved == 1) ? " data item" : " data items");
				reportUserMessage(message + " successfully saved.");
				if (getCurrentData() != null)
				{
					return DISPLAY;
				}
				return LIST;
			}
		} catch (Throwable error) 
		{
			reportError(error, "Error saving data items");
			return ERROR;
		}
	}

	@SkipValidation
	public String reload() {
		try {
			resolveConcepts();
			return INPUT;
		} catch (Throwable error) {
			reportError(error, "Error resolving UserDataItem properties");
			return ERROR;
		}
	}

	@SkipValidation
	public String cancel() {
		// discard input and return
		addActionMessage("File not saved.");
		UserDataItem currentData = getCurrentData();
		if (getCurrentData() == null)
			return LIST;
		else {
			setDataItem(currentData);
			return DISPLAY;
		}
	}

	/*================================================================
	 * Upload form property accessor methods
	 *================================================================*/
	public UserDataItem getDataItem() {
		try {
			if (dataItem == null)
				setDataItem(getWorkbenchSession().getUserDataItemInstance(getCurrentFolder()));
			return dataItem;
		} catch (Throwable error) {
			reportError(error, "Error retrieving UserDataItem instance");
			return null;
		}
	}

	public void setDataItem(UserDataItem dataItem) {
		this.dataItem = dataItem;
	}

	public String getLabel() {		
		if (dataItem != null)
		{
			return dataItem.getLabel();
		}
		else return null;
	}

	public void setLabel(String label) {
		try {
			if (dataItem == null)
				setDataItem(getWorkbenchSession().getUserDataItemInstance(getCurrentFolder()));
			dataItem.setLabel(label);
		} catch (Throwable error) {
			reportError(error, "Error retrieving UserDataItem instance");
		}
	}

	@SuppressWarnings("unchecked")
	public Map<Integer, Object[]> getUploadedFiles() {
		return (Map<Integer, Object[]>)getSessionAttribute(UPLOADED_FILES);
	}

	public void setUploadedFiles(Map<Integer, Object[]> files) {
		if (files == null || files.size() < 1)
			clearUploadedFiles();
		else setSessionAttribute(UPLOADED_FILES, files);
	}

	public void clearUploadedFiles() {
		clearSessionAttribute(UPLOADED_FILES);
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = stripCarriageReturns(source);
	}

	public File[] getUpload() {
		return uploads;
	}

	// This function is called automatically by strut after user upload file(s)
	public void setUpload(File[] uploads) {
		this.uploads = uploads;
	}

	public String[] getUploadFileName() {
		return uploadFileNames;
	}

	public void setUploadFileName(String[] uploadFileNames) {
		this.uploadFileNames = uploadFileNames;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public String getDataFormat() {
		return dataFormat;
	}

	public void setDataFormat(String dataFormat) {
		this.dataFormat = dataFormat;
	}

	/*================================================================
	 * User interface property accessor methods
	 *================================================================*/
	public Map<String, String> getEntityTypes() {
		try {
			return mapConceptSet(getEntityTypeSet(), "EntityType");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of entity types");
			return null;
		}
	}

	public Map<String, String> getDataTypes() {
		String entityType = getEntityType();
		try {
			if (entityType == null || entityType.equals("") ||
				entityType.equals(UNKNOWN))
				return mapConceptSet(getUnknownDataTypeSet(), "DataType");
			else return mapConceptSet(
				getDataTypeSet(EntityType.valueOf(entityType)), "DataType");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of data types");
			return null;
		}
	}

	public Map<String, String> getDataFormats() {
		String entityType = getEntityType();
		String dataType = getDataType();
		try {
			if (entityType == null || entityType.equals("") || entityType.equals(UNKNOWN) ||
				dataType == null || dataType.equals("") || dataType.equals(UNKNOWN))
				return mapConceptSet(getUnknownDataFormatSet(), "DataFormat");
			else return mapConceptSet(
				getDataFormatSet(EntityType.valueOf(entityType), DataType.valueOf(dataType)),
					"DataFormat");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of registered data formats for " +
				"specified entity type and data type");
			return null;
		}
	}

	/*================================================================
	 * Convenience methods
	 *================================================================*/
	protected void addUploadedFiles(Map<Integer, Object[]> files) 
	{
		if (files != null && files.size() > 0) 
		{
			Map<Integer, Object[]> uploadedFiles = getUploadedFiles();
			if (uploadedFiles == null)
			{
				uploadedFiles = new HashMap<Integer, Object[]>(files.size());
			}
			for (Integer index : files.keySet()) 
			{
				uploadedFiles.put(index, files.get(index));
				reportUserMessage("File \"" + (String)(files.get(index)[1]) + "\" added to session cache of uploaded files.");
			}
			setUploadedFiles(uploadedFiles);
		}
	}

	protected void saveDataPreferences() {
		if (resolveConcepts() == false)
			return;
		else {
			Folder currentFolder = getCurrentFolder();
			FolderController controller = getFolderController();
			controller.setFolderPreference(currentFolder, UPLOAD_ENTITY_TYPE, getEntityType());
			controller.setFolderPreference(currentFolder, UPLOAD_DATA_TYPE, getDataType());
			controller.setFolderPreference(currentFolder, UPLOAD_DATA_FORMAT, getDataFormat());
			try {
				getWorkbenchSession().saveFolder(currentFolder);
			} catch (Throwable error) {
				reportError(error, "Error saving data upload preferences for the current folder");
			}
		}
	}

	protected int saveDataItems() 
	{
		int saved = 0;
		
		// Get data pasted into "source" field of form.
		String data = stripCarriageReturns ( getSource().trim() );

		// Get files uploaded in form (fields are named uploads and uploadFileNames.
		File uploads[] = getUpload();
		String filenames[] = getUploadFileName();

		String label = null;
			
		// If there is a paste, save it
		if ( data != null && data.length() > 0 )
		{
			label = getSourceLabel();
			
			try 
			{
				setCurrentData ( saveDataItem ( label, data, null ) );
				saved++;
			}
			catch ( Throwable error ) 
			{
				reportUserError ( error, "--Error saving data item \"" + label + "\"" + ":" + error.toString());
				return 0;
			}
		}
				
		// If there are uploads (by themselves or in addition to pasted data), uploads will be non-null. 
		if (uploads != null)
		{
			for ( int i = 0; i < uploads.length; i++ )
			{
				/*  
					User can select multiple files to upload but we only have one label
					field so we will use the filename as the label for each item and not
					let the user choose.
				*/
				label = filenames[i];
				
				try 
				{
					saveDataItem ( label, data, uploads[i] );
					saved++;
				} catch (Throwable error) 
				{
					reportUserError(error, "Error saving data item \"" + label + "\"");
					return 0;
				}
			}
		} 

		refreshFolderDataTabs();
		return saved;
	}

	/*
	*/
	protected UserDataItem saveDataItem(String label, String data, File upload) throws WorkbenchException 
	{
		// get current folder
		Folder folder = getCurrentFolder();
		if (folder == null)
		{
			throw new WorkbenchException("You must select a folder " + "to save a data item.");
		}
		// get data label
		if (label == null || label.equals(""))
		{
			throw new WorkbenchException("You must provide a label " + "to save a data item.");
		}
		// get data contents
		if (upload == null)
		{
			if ( data == null || data.equals(""))
			{
				throw new WorkbenchException("You must provide some source data " + "to save a data item.");
			}
		}
		EntityType entityType = EntityType.valueOf(UNKNOWN);
		DataType dataType =  DataType.valueOf(UNKNOWN);
		DataFormat dataFormat = DataFormat.valueOf(UNKNOWN);

		/*
			Terri added this code to test out the ability to guess the dataformat.  I made it conditional
			on the type being UNKNOWN, UNKNOWN, UNKNOWN so that it shouldn't do any harm in ngbw, but
			I don't think that's what the condition should really be in cipres and I'm not sure if what it
			should be in ngbw.  But I figured this way at least we shouldn't be erasing any useful info that
			the user entered.

			BUG, I think: file size in bytes isn't being displayed when we set dataformat with this method.
		*/
		if (upload == null)
		{
			if (dataFormat == DataFormat.valueOf(UNKNOWN))
			{
				dataFormat = DataDetails.diagnoseFormat(data);
			}
		}
				
		// retrieve data item instance
		UserDataItem dataItem = null;
		try 
		{
			dataItem = getWorkbenchSession().getUserDataItemInstance(folder);
		} catch (Throwable error) 
		{
			throw new WorkbenchException(error);
		}
		// populate data item
		dataItem.setLabel(label);
		dataItem.setEntityType(entityType);
		dataItem.setDataType(dataType);
		dataItem.setDataFormat(dataFormat);

		if (upload != null)
		{
			FileInputStream is;
			try 
			{
				Workbench.convertEncoding(upload);
				is = new FileInputStream(upload);
				dataItem.setData(is);
			} catch (Exception e) 
			{
				throw new WorkbenchException(e);
			}
		} else
		{
			dataItem.setData(data);
		}
		try 
		{
			dataItem = getWorkbenchSession().saveUserDataItem(dataItem, folder);
		} catch (Throwable error) 
		{
			throw new WorkbenchException(error);
		}
		return dataItem;
	}

	protected boolean resolveConcepts() 
	{
		String entityType = getEntityType();
		String dataType = getDataType();
		String dataFormat = getDataFormat();
		if (entityType == null || entityType.equals("")) 
		{
			setEntityType(null);
			setDataType(null);
			setDataFormat(null);
			return false;
		} else 
		{
			Set<DataType> dataTypes = getDataTypeSet(EntityType.valueOf(entityType));
			if (dataType == null || dataType.equals("") || dataTypes == null ||
				dataTypes.contains(DataType.valueOf(dataType)) == false) 
				{
				setDataType(null);
				setDataFormat(null);
				return false;
			} else 
			{
				Set<DataFormat> dataFormats =
					getDataFormatSet(EntityType.valueOf(entityType), DataType.valueOf(dataType));
				if (dataFormat == null || dataFormat.equals("") || dataFormats == null ||
					dataFormats.contains(DataFormat.valueOf(dataFormat)) == false) 
					{
					setDataFormat(null);
					return false;
				} else return true;
			}
		}
	}

	protected boolean validateData() 
	{
		UserDataItem dataItem = getDataItem();
		if (dataItem == null)
			return false;
		else 
		{
			String label = getLabel();
			if (label == null || label.equals("")) 
			{
				addFieldError("label", "Label is required.");
				return false;
			}
			String source = getSource();
			File upload[] = getUpload();
			
			/* MONA: updated needed!
			if ((source == null || source.equals("")) &&
				(upload == null || upload.canRead() == false)) 
			{
				addActionError("You must either upload a file or enter your data directly.");
				return false;
			} else 
			{
				return true;
			}
			*/
			return true;
		}
	}

	protected boolean validateUpload() {
		Map<Integer, Object[]> uploads = getUploadedFiles();
		if (uploads == null || uploads.size() < 1) {
			addActionError("You must select one or more files to upload.");
		}
		if (hasFieldErrors())
			return false;
		else return true;
	}

	/*
		Terri: don't we want to limit chars that can be used in labels?
		BaseValidator.isSimpleFilename?
	*/
	protected boolean validatePaste() {
		String label = getSourceLabel();
		String filenames[] = getUploadFileName();
		if ( ( label == null || label.equals ( "" ) ) && filenames == null )
		{
			addFieldError("label", "You must provide a label " +
				"if you are entering your data directly.");
		}
		String source = getSource();
		File uploads[] = getUpload();
		if ((source == null || source.equals("")) &&
			( uploads == null || uploads.length == 0 ) )
		{
			//(uploads == null || upload.canRead() == false)) {
			addActionError("You must either upload a file or " +
				"enter your data directly.");
			return false;
		}
		if (hasFieldErrors())
			return false;
		else return true;
	}


}
