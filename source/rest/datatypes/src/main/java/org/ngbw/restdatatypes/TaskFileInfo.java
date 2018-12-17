/*
 * TaskFileInfo
 */
package org.ngbw.restdatatypes;


import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;


/**
 *
 * @author Paul Hoover
 *
 */
@XmlRootElement(name = "file")
@XmlAccessorType(XmlAccessType.FIELD)
public class TaskFileInfo {

	// data fields


	public Long id;
	public Long length;
	public String name;
	public String parameter;
	public LinkData link;
	public Date dateCreated;


	// constructors


	public TaskFileInfo()
	{

	}
}
