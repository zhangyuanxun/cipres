/*
 * CreateHomeFolders.java
 */
package org.ngbw.utils;


import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.ngbw.sdk.database.ConnectionManager;
import org.ngbw.sdk.database.DriverConnectionSource;
import org.ngbw.sdk.database.Folder;
import org.ngbw.sdk.database.User;


/**
 *
 * @author Paul Hoover
 *
 */
public class CreateHomeFolders {

	// public methods


	public static void main(String[] args)
	{
		try {
			ConnectionManager.setConnectionSource(new DriverConnectionSource());

			Connection dbConn = ConnectionManager.getConnectionSource().getConnection();

			try {
				int totalProcessed = 0;
				List<User> users = User.findAllUsers();

				for (User user : users) {
					Folder homeFolder = findOrCreateHomeFolder(dbConn, user);

					updateRootFolders(dbConn, homeFolder.getFolderId(), user.getUserId());
					updateRootItems(dbConn, homeFolder.getFolderId(), user.getUserId());
					updateRootTasks(dbConn, homeFolder.getFolderId(), user.getUserId());

					totalProcessed += 1;

					if (totalProcessed % 100 == 0)
						System.out.println("processed " + totalProcessed + " users...");
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


	private static Folder findOrCreateHomeFolder(Connection dbConn, User owner) throws IOException, SQLException
	{
		PreparedStatement selectStmt = dbConn.prepareStatement("SELECT FOLDER_ID FROM folders WHERE LABEL = ? AND USER_ID = ? AND ENCLOSING_FOLDER_ID IS NULL");
		ResultSet rows = null;

		try {
			selectStmt.setString(1, owner.getUsername());
			selectStmt.setLong(2, owner.getUserId());

			rows = selectStmt.executeQuery();

			Folder result;

			if (rows.next()) {
				result = new Folder(rows.getLong(1));
			}
			else {
				result = new Folder(owner);

				result.setLabel(owner.getUsername());
				result.save();
			}

			return result;
		}
		finally {
			if (rows != null)
				rows.close();

			selectStmt.close();
		}
	}

	private static void updateRootFolders(Connection dbConn, long folderId, long userId) throws SQLException
	{
		PreparedStatement updateStmt = dbConn.prepareStatement("UPDATE folders SET ENCLOSING_FOLDER_ID = ? WHERE FOLDER_ID != ? AND USER_ID = ? AND ENCLOSING_FOLDER_ID IS NULL");

		try {
			updateStmt.setLong(1, folderId);
			updateStmt.setLong(2, folderId);
			updateStmt.setLong(3, userId);

			updateStmt.executeUpdate();
		}
		finally {
			updateStmt.close();
		}
	}

	private static void updateRootItems(Connection dbConn, long folderId, long userId) throws SQLException
	{
		PreparedStatement updateStmt = dbConn.prepareStatement("UPDATE userdata SET ENCLOSING_FOLDER_ID = ? WHERE USER_ID = ? AND ENCLOSING_FOLDER_ID IS NULL");

		try {
			updateStmt.setLong(1, folderId);
			updateStmt.setLong(2, userId);

			updateStmt.executeUpdate();
		}
		finally {
			updateStmt.close();
		}
	}

	private static void updateRootTasks(Connection dbConn, long folderId, long userId) throws SQLException
	{
		PreparedStatement updateStmt = dbConn.prepareStatement("UPDATE tasks SET ENCLOSING_FOLDER_ID = ? WHERE USER_ID = ? AND ENCLOSING_FOLDER_ID IS NULL");

		try {
			updateStmt.setLong(1, folderId);
			updateStmt.setLong(2, userId);

			updateStmt.executeUpdate();
		}
		finally {
			updateStmt.close();
		}
	}
}
