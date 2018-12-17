/*
 * FolderItem.java
 */
package org.ngbw.sdk.database;


import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ngbw.sdk.Workbench;
import org.ngbw.sdk.WorkbenchException;


/**
 * The <code>FolderItem</code> class specifies the common functionality
 * of all data in the user data area that can be contained
 * within a folder (= enclosing folder).
 *
 * @author Roland H. Niedner
 * @author Paul Hoover
 *
 */
public abstract class FolderItem extends VersionedRow {

	// nested classes


	/**
	 *
	 */
	private static abstract class LabelPolicy {

		// public methods


		public abstract void enforce(Connection dbConn, FolderItem item) throws IOException, SQLException;


		// protected methods


		protected boolean isUnique(Connection dbConn, Long enclosingFolderId, String label) throws IOException, SQLException
		{
			Column<Integer> result = new IntegerColumn("result", true);
			List<Criterion> criteria = new ArrayList<Criterion>();

			criteria.add(new LongCriterion("ENCLOSING_FOLDER_ID", enclosingFolderId));
			criteria.add(new StringCriterion("LABEL", label));

			(new CountOp("userdata", criteria, result)).execute(dbConn);

			int count = result.getValue();

			if (count > 0)
				return false;

			(new CountOp("tasks", criteria, result)).execute(dbConn);

			count += result.getValue();

			if (count > 0)
				return false;

			(new CountOp("folders", criteria, result)).execute(dbConn);

			count += result.getValue();

			return count < 1;
		}
	}

	/**
	 *
	 */
	private static class RenamePolicy extends LabelPolicy {

		// data fields


		private static Pattern m_extensionPattern = Pattern.compile(".+(\\.\\S+)$");
		private static Pattern m_versionPattern = Pattern.compile(".+_(\\d+)$");


		// public methods


		@Override
		public void enforce(Connection dbConn, FolderItem item) throws IOException, SQLException
		{
			if (!isUnique(dbConn, item.getEnclosingFolderId(), item.getLabel())) {
				String label = item.getLabel();
				Matcher match = m_extensionPattern.matcher(label);
				String baseName;
				String extension;

				if (match.matches()) {
					int index = match.start(1);

					baseName = label.substring(0, index);
					extension = label.substring(index);
				}
				else {
					baseName = label;
					extension = "";
				}

				int version;

				match = m_versionPattern.matcher(baseName);

				if (match.matches()) {
					int previous = Integer.parseInt(match.group(1));

					version = previous + 1;
					baseName = baseName.substring(0, baseName.length() - 2);
				}
				else
					version = 1;

				while(true) {
					String newLabel = baseName + "_" + version + extension;

					if (newLabel.length() > item.m_maxLabelLength) {
						int newLength = baseName.length() - (newLabel.length() - item.m_maxLabelLength);

						baseName = baseName.substring(0, newLength);

						newLabel = baseName + "_" + version + extension;
					}

					item.setLabel(newLabel);

					if (isUnique(dbConn, item.getEnclosingFolderId(), item.getLabel()))
						break;

					version += 1;
				}
			}
		}
	}

	/**
	 *
	 */
	private static class RejectPolicy extends LabelPolicy {

		// public methods


		@Override
		public void enforce(Connection dbConn, FolderItem item) throws IOException, SQLException
		{
			if (!isUnique(dbConn, item.getEnclosingFolderId(), item.getLabel()))
				throw new WorkbenchException("duplicate label " + item.getLabel() + " in Folder " + item.getEnclosingFolderId());
		}
	}


	// data fields


	public static final String SEPARATOR = "/";
	private static LabelPolicy m_policy;
	private final int m_maxLabelLength;
	private final Column<Long> m_groupId = new LongColumn("GROUP_ID", false);
	private final Column<Long> m_userId = new LongColumn("USER_ID", false);
	private final Column<Date> m_creationDate = new DateColumn("CREATION_DATE", false);
	private final Column<Long> m_enclosingFolderId = new LongColumn("ENCLOSING_FOLDER_ID", true);
	private final Column<String> m_label;


	static {
		String renameLabels = Workbench.getInstance().getProperties().getProperty("database.renameDuplicateLabels");

		if (Boolean.parseBoolean(renameLabels))
			m_policy = new RenamePolicy();
		else
			m_policy = new RejectPolicy();
	}


	// constructors


	/**
	 *
	 * @param tableName
	 * @param keyName
	 */
	protected FolderItem(String tableName, String keyName, int maxLabelLength)
	{
		super(tableName, keyName);

		m_maxLabelLength = maxLabelLength;
		m_label = new StringColumn("LABEL", false, m_maxLabelLength);
	}


	// public methods


	/**
	 * Method returns the id of the group that owns this folder item.
	 *
	 * @return group id
	 */
	public long getGroupId()
	{
		return m_groupId.getValue();
	}

	/**
	 * Method set the id of the group that owns this folder item.
	 *
	 * @param groupId
	 */
	public void setGroupId(Long groupId)
	{
		m_groupId.setValue(groupId);
	}

	/**
	 * Method returns an instance of the group that owns this folder item.
	 *
	 * @return group
	 */
	public Group getGroup() throws IOException, SQLException
	{
		if (m_groupId.isNull())
			return null;

		return new Group(m_groupId.getValue());
	}

	/**
	 * Method set the group that owns this folder item.
	 *
	 * @param group
	 */
	public void setGroup(Group group)
	{
		if (group != null && !group.isNew())
			setGroupId(group.getGroupId());
		else
			setGroupId(null);
	}

	/**
	 * Method returns the id of the user that owns this folder item.
	 *
	 * @return owner id
	 */
	public long getUserId()
	{
		return m_userId.getValue();
	}

	/**
	 * Method set the id of the user that owns this folder item.
	 *
	 * @param userId
	 */
	public void setUserId(Long userId)
	{
		m_userId.setValue(userId);
	}

	/**
	 * Method returns an instance of the user that owns this folder item.
	 *
	 * @return owner
	 */
	public User getUser() throws IOException, SQLException
	{
		if (m_userId.isNull())
			return null;

		return new User(m_userId.getValue());
	}

	/**
	 * Method set the user that owns this folder item.
	 *
	 * @param user
	 */
	public void setUser(User user)
	{
		if (user != null && !user.isNew())
			setUserId(user.getUserId());
		else
			setUserId(null);
	}

	/**
	 * Method returns the creation date of this folder item.
	 *
	 * @return creationDate
	 */
	public Date getCreationDate()
	{
		return m_creationDate.getValue();
	}

	/**
	 * Method returns the label (to be displayed by the GUI) of this folder item.
	 *
	 * @return label
	 */
	public String getLabel()
	{
		return m_label.getValue();
	}

	/**
	 * Method sets the label (to be displayed by the GUI) of this folder item.
	 *
	 * @param label
	 */
	public void setLabel(String label)
	{
		if (label == null || label.isEmpty())
			throw new WorkbenchException("Label can't be empty");

		m_label.setValue(label);
	}

	/**
	 *
	 * @return
	 */
	public abstract String getUUID();

	/**
	 *
	 * @param uuid
	 */
	public abstract void setUUID(String uuid);

	/**
	 * Method returns the enclosing folder id.
	 *
	 * @return enclosingFolder id
	 */
	public long getEnclosingFolderId()
	{
		return m_enclosingFolderId.getValue();
	}

	/**
	 * Method sets the enclosing folder id.
	 *
	 * @param enclosingFolderId
	 */
	public void setEnclosingFolderId(Long enclosingFolderId)
	{
		m_enclosingFolderId.setValue(enclosingFolderId);
	}

	/**
	 * Method returns an instance of the enclosing folder.
	 *
	 * @return enclosingFolder instance
	 */
	public Folder getEnclosingFolder() throws IOException, SQLException
	{
		if (m_enclosingFolderId.isNull())
			return null;

		return new Folder(m_enclosingFolderId.getValue());
	}

	/**
	 * Method sets the enclosing folder.
	 *
	 * @param enclosingFolder enclosingFolder
	 */
	public void setEnclosingFolder(Folder enclosingFolder)
	{
		if (enclosingFolder != null && !enclosingFolder.isNew())
			setEnclosingFolderId(enclosingFolder.getFolderId());
		else
			setEnclosingFolderId(null);
	}

	/**
	 *
	 * @return
	 */
	public static String generateUUID()
	{
		return UUID.randomUUID().toString().toUpperCase().replaceAll("-", "");
	}

	/**
	 *
	 * @param path
	 * @return
	 */
	public static String escapePath(String path)
	{
		return path.replaceAll("(/|\\\\)", "\\\\$1");
	}


	// package methods


	/**
	 * Saves the current state of the object to the database. If the object has not yet been persisted,
	 * new records are inserted in the appropriate tables. If the object has been persisted, then any
	 * changes are written to the backing tables. Only those values that have changed are written, and
	 * if the state of the object is unchanged, the method does nothing.
	 *
	 * @param dbConn a <code>Connection</code> object that will be used to communicate with the database
	 * @throws IOException
	 * @throws SQLException
	 */
	@Override
	void save(Connection dbConn) throws IOException, SQLException
	{
		if (isNew()) {
			pushInsertOps();

			saveUniqueLabel(dbConn);
		}
		else {
			pushUpdateOps();

			if (m_label.isModified() || m_enclosingFolderId.isModified())
				saveUniqueLabel(dbConn);
			else
				executeOps(dbConn);
		}
	}


	// protected methods


	/**
	 *
	 * @param creationDate
	 */
	protected void setCreationDate(Date creationDate)
	{
		m_creationDate.setValue(creationDate);
	}

	/**
	 *
	 * @param columns
	 */
	@Override
	protected void construct(Column<?>... columns)
	{
		Column<?>[] allColumns = new Column<?>[columns.length + 5];

		System.arraycopy(columns, 0, allColumns, 0, columns.length);

		allColumns[columns.length] = m_groupId;
		allColumns[columns.length + 1] = m_userId;
		allColumns[columns.length + 2] = m_creationDate;
		allColumns[columns.length + 3] = m_label;
		allColumns[columns.length + 4] = m_enclosingFolderId;

		super.construct(allColumns);
	}


	// private methods


	private void saveUniqueLabel(Connection dbConn) throws IOException, SQLException
	{
		while (true) {
			try {
				m_policy.enforce(dbConn, this);

				executeOps(dbConn);

				break;
			}
			catch (SQLException sqlErr) {
				String state = sqlErr.getSQLState();

				if ((!state.equals("23000") && !state.equals("23505")) || sqlErr.getMessage().indexOf(m_label.getValue()) < 0)
					throw sqlErr;
			}
		}
	}
}
