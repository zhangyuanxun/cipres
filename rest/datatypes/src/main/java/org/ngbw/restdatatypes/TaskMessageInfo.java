/*
 * TaskMessageInfo.java
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
@XmlRootElement(name = "message")
@XmlAccessorType(XmlAccessType.FIELD)
public class TaskMessageInfo {

	// data fields


	public String text;
	public String stage;
	public Date timestamp;


	// constructors


	public TaskMessageInfo()
	{

	}
}
