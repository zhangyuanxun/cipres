/*
 * FolderItemInfo.java
 */
package org.ngbw.restdatatypes;


import java.util.Date;


/**
 *
 * @author Paul Hoover
 *
 */
public abstract class FolderItemInfo {

	// data fields


	public String uuid;
	public String path;
	public String label;
	public String owner;
	public LinkData link;
	public Date dateCreated;


	// constructors


	public FolderItemInfo()
	{

	}

	protected FolderItemInfo(String rel)
	{
		link = new LinkData();

		link.rel = rel;
	}
}
