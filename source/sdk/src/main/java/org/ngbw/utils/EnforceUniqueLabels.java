/*
 * EnforceUniqueLabels.java
 */
package org.ngbw.utils;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.DriverConnectionSource;


/**
 *
 * @author Paul Hoover
 *
 */
public class EnforceUniqueLabels {

	// nested classes


	private static class FolderItemInfo {

		public final long id;
		public final String table;
		public final String field;
		public final String label;


		public FolderItemInfo(long itemId, String tableName, String fieldName, String itemLabel)
		{
			id = itemId;
			table = tableName;
			field = fieldName;
			label = itemLabel;
		}
	}


	// data fields


	private static final int FETCH_SIZE = 100;


	// public methods


	public static void main(String[] args)
	{
		try {
			ConnectionManager.setConnectionSource(new DriverConnectionSource());

			Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

			try {
				List<Long> folderIds = findFolderIds(dbConn);
				int numExamined = 0;

				for (Long folderId : folderIds) {
					List<FolderItemInfo> itemList = new ArrayList<FolderItemInfo>();

					findFolderItems(dbConn, "folders", "FOLDER_ID", folderId, itemList);
					findFolderItems(dbConn, "tasks", "TASK_ID", folderId, itemList);
					findFolderItems(dbConn, "userdata", "USERDATA_ID", folderId, itemList);

					Pattern extensionPattern = Pattern.compile(".+(\\.\\S+)$");
					Pattern versionPattern = Pattern.compile(".+_(\\d+)$");

					for (FolderItemInfo info : itemList) {
						if (!isUnique(dbConn, info.label, folderId)) {
							Matcher match = extensionPattern.matcher(info.label);
							String baseName;
							String extension;

							if (match.matches()) {
								int index = match.start(1);

								baseName = info.label.substring(0, index);
								extension = info.label.substring(index);
							}
							else {
								baseName = info.label;
								extension = "";
							}

							int version;

							match = versionPattern.matcher(baseName);

							if (match.matches()) {
								int previous = Integer.parseInt(match.group(1));

								version = previous + 1;
								baseName = baseName.substring(0, baseName.length() - 2);
							}
							else
								version = 1;

							int maxLabelLength;

							if (info.table.equals("userdata"))
								maxLabelLength = 1023;
							else
								maxLabelLength = 255;

							while (true) {
								String newLabel = baseName + "_" + version + extension;

								if (newLabel.length() > maxLabelLength) {
									int newLength = baseName.length() - (newLabel.length() - maxLabelLength);

									baseName = baseName.substring(0, newLength);

									newLabel = baseName + "_" + version + extension;
								}

								updateLabel(dbConn, info.table, info.field, info.id, newLabel);

								if (isUnique(dbConn, newLabel, folderId)) {
									System.out.println("changed label for " + info.table + " " + info.id + " in folder " + folderId + " from '" + info.label + "' to '" + newLabel + "'");

									break;
								}

								version += 1;
							}
						}

						numExamined += 1;

						if (numExamined % 100 == 0)
							System.out.println("examined " + numExamined + " items...");
					}
				}
			}
			finally {
				dbConn.close();
			}

			System.out.println("finished");
		}
		catch (Exception err) {
			err.printStackTrace(System.err);

			System.exit(-1);
		}
	}


	// private methods


	private static List<Long> findFolderIds(Connection dbConn) throws SQLException
	{
		Statement selectStmt = dbConn.createStatement();
		ResultSet rows = null;

		try {
			selectStmt.setFetchSize(FETCH_SIZE);

			rows = selectStmt.executeQuery("SELECT FOLDER_ID FROM folders");

			List<Long> result = new ArrayList<Long>();

			result.add(null);

			while (rows.next())
				result.add(rows.getLong(1));

			return result;
		}
		finally {
			if (rows != null)
				rows.close();

			selectStmt.close();
		}
	}

	private static void findFolderItems(Connection dbConn, String tableName, String fieldName, Long enclosingFolderId, List<FolderItemInfo> items) throws SQLException
	{
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT " + fieldName + ", LABEL FROM " + tableName + " WHERE ENCLOSING_FOLDER_ID = ?");
		ResultSet rows = null;

		try {
			if (enclosingFolderId != null)
				selectStmt.setLong(1, enclosingFolderId);
			else
				selectStmt.setNull(1, Types.BIGINT);

			rows = selectStmt.executeQuery();

			while (rows.next())
				items.add(new FolderItemInfo(rows.getLong(1), tableName, fieldName, rows.getString(2)));
		}
		finally {
			if (rows != null)
				rows.close();

			selectStmt.close();
		}
	}

	private static boolean isUnique(Connection dbConn, String label, Long enclosingFolderId) throws SQLException
	{
		int count = getLabelCount(dbConn, "userdata", label, enclosingFolderId);

		if (count > 1)
			return false;

		count += getLabelCount(dbConn, "tasks", label, enclosingFolderId);

		if (count > 1)
			return false;

		count += getLabelCount(dbConn, "folders", label, enclosingFolderId);

		return count < 2;
	}

	private static int getLabelCount(Connection dbConn, String tableName, String label, Long enclosingFolderId) throws SQLException
	{
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT COUNT(*) FROM " + tableName + " WHERE LABEL = ? AND ENCLOSING_FOLDER_ID = ?");
		ResultSet row = null;

		try {
			selectStmt.setString(1, label);

			if (enclosingFolderId != null)
				selectStmt.setLong(2, enclosingFolderId);
			else
				selectStmt.setNull(2, Types.BIGINT);

			row = selectStmt.executeQuery();

			if (row.next())
				return row.getInt(1);
			else
				return 0;
		}
		finally {
			if (row != null)
				row.close();

			selectStmt.close();
		}
	}

	private static void updateLabel(Connection dbConn, String tableName, String fieldName, long itemId, String label) throws SQLException
	{
		PreparedStatement updateStmt = dbConn.prepareStatement("UPDATE " + tableName + " SET LABEL = ? WHERE " + fieldName + " = ?");

		try {
			updateStmt.setString(1, label);
			updateStmt.setLong(2, itemId);

			updateStmt.executeUpdate();
		}
		finally {
			updateStmt.close();
		}
	}
}
