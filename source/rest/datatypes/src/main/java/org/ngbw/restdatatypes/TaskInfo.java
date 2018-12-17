/*
 * TaskInfo.java
 */
package org.ngbw.restdatatypes;


import java.util.Collection;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;


/**
 *
 * @author Paul Hoover
 *
 */
@XmlRootElement(name = "task")
@XmlAccessorType(XmlAccessType.FIELD)
public class TaskInfo extends FolderItemInfo {

	// data fields


	public String tool;
	public String handle;
	public String stage;
	public Boolean failed;

	@XmlElementWrapper(name = "inputs")
	@XmlElement(name = "input")
	public Collection<TaskFileInfo> inputs;

	@XmlElementWrapper(name = "outputs")
	@XmlElement(name = "output")
	public Collection<TaskFileInfo> outputs;

	@XmlElementWrapper(name = "messages")
	@XmlElement(name = "message")
	public Collection<TaskMessageInfo> messages;


	// constructors


	public TaskInfo()
	{
		super("task");
	}
}
