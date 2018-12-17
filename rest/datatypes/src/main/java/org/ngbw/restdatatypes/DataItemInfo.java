/*
 * FileInfo.java
 */
package org.ngbw.restdatatypes;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;


/**
 *
 * @author Paul Hoover
 *
 */
@XmlRootElement(name = "dataitem")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataItemInfo extends FolderItemInfo {

	// data fields


	public Long length;


	// constructors


	public DataItemInfo()
	{
		super("dataitem");
	}
}
