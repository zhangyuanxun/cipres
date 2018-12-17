package org.ngbw.web.actions;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.core.GenericDataRecordCollection;
import org.ngbw.sdk.core.shared.IndexedDataRecord;
import org.ngbw.sdk.core.types.DataFormat;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.core.types.RecordFieldType;
import org.ngbw.sdk.core.types.RecordType;
import org.ngbw.sdk.database.DataRecord;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.FolderItem;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.User;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.sdk.database.util.UserDataItemSortableField;
import org.ngbw.web.model.Page;
import org.ngbw.web.model.Tab;
import org.ngbw.web.model.TabbedPanel;
import org.ngbw.web.model.impl.ConceptComparator;
import org.ngbw.web.model.impl.ListPage;
import org.ngbw.web.model.impl.RecordFieldTypeComparator;

@SuppressWarnings("serial")
public class DataEditor extends DataManager
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(DataEditor.class.getName());

	private String data;
	public String getData() { return data; }

	public void setData(String data) 
	{ 
		this.data = data; 
	}

	private long dataSize;
	public long getDataSize() { return dataSize; }
	public void setDataSize(long size) { this.dataSize = size; }

	public long getMaxSize() { return MAX_DATA_SIZE; }

	// TODO: if document is too large, addActionMessage and return DISPLAY (and make
	// struts.xml handle DISPLAY return.  How to prevent user from adding too much data?
	public String input()
	{
		try
		{
			SourceDocument sd = getSourceDocument();
			if (sd == null)
			{
				addActionError("Error retrieving document.  No source document found.");
				return "list";
			}
			long size = sd.getDataLength();
			if (sd.getDataLength() > MAX_DATA_SIZE)
			{
				addActionError("Sorry, this file is too large to edit online.");
				return "list";
			}
			data = getSourceDataAsString(getSourceDocument());
			return INPUT;
		}
		catch (Exception e)
		{
			logger.error(getUsernameString(), e);
			addActionError("Error retrieving document.");
			return "list";
		}
	}

	public String execute()
	{
		logger.error("In execute: this may mean that the tomcat connector's maxPostSize was reached. " +
			" It should be set to -1 for unlimited or slightly larger than max.data.display.size from build.properties.");
		addActionError("In internal error occurred while saving the changes.  Please report this error.");
		return "list";
	}

	/* 
		Save As New - create a new UserDataItem using the copy ctor and then
		call setData() on it.  UserDataItem will automatically change the label to 
		include a version number.

		SaveAndOverWrite - we just call setData on the existing UserDataItem.
		Internally, SourceDocumentRow.java updates the source document if it
		isn't referenced elsewhere; otherwise it creates a new source doc.
	*/
	protected String save(boolean overwrite)
	{
		UserDataItem newItem;
		UserDataItem currentItem;
		try
		{
			currentItem = getCurrentData();
			if (currentItem == null)
			{
				throw new Exception("No current UserDataItem");
			}
			if (!overwrite)
			{
				newItem = new UserDataItem(currentItem);
			} else
			{
				newItem = currentItem;
			}
			newItem.setData(stripCarriageReturns(data.trim()));
			newItem.save();
			addActionMessage("Saved changes to " + newItem.getLabel());
			refreshFolderDataTabs();
			return "list";
		}
		catch(Exception e)
		{
			logger.error(getUsernameString() + "4 Error saving changes from editor ", e);
			addActionError("Error saving changes.");
			return "list";
		}
	}

	public String saveAsNew()
	{
		return save(false);
	}

	public String saveAndOverwrite()
	{
		return save(true);
	}


	public String cancel()
	{
		return "list";
	}


}
