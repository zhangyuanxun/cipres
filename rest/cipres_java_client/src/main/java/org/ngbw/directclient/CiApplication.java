package org.ngbw.directclient; 

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton class that can be used to configure a CIPRES client application from a properties file.  

	Three locations are searched, in order.  
	Files later in the order override earlier property settings if multiple files are found:
			<ul>
            <li> $SDK_VERSIONS/testdata/pycipres.conf 
            <li> System.getProperty("user.home")/pycipres.conf
            <li> $PYCIPRES
			</ul>
        where SDK_VERSIONS, HOME and PYCIPRES are environment variables.
		<p>
        Required properties are:
			<ul>
            <li>URL (for example https://cipresrest.sdsc.edu/cipresrest/v1)
            <li>APPNAME
            <li>APPID
            <li>USERNAME
            <li>PASSWORD
			</ul>
 */
public class CiApplication
{
	private static final Logger log = LoggerFactory.getLogger(CiApplication.class.getName());

	private static CiApplication SINGLETON;
	private Properties properties;

	private String restUrl;
	private String appname;
	private String appkey; 
	private String username;
	private String password;

	/**
	 * Gets the url of the CIPRES REST API.
	 * @return the url
	 */
	public String getRestUrl() { return restUrl; }
	
	
	/**
	 * Gets the name of the registered client application.
	 * @return the appname
	 */
	public String getAppname() { return appname; }
	
	/**
	 * Gets the application ID of the registered client application.
	 * @return the app ID.
	 */
	public String getAppKey() { return appkey; }
	
	/**
	 * Gets the username of the registered CIPRES REST API user.
	 * @return the username
	 */
	public String getUsername() { return username; }
	
	/**
	 * Gets the user's password.
	 * @return the password
	 */
	public String getPassword() { return password; }

	/**
	 * Instantiate the CiApplication (and read the properties file), or 
	 * return the previously instantiated instance.
	 * @return CiApplication
	 * Will throw a runtime exception if any of the required properties are missing.
	 */
	public static synchronized CiApplication getInstance() 
	{
		if (SINGLETON == null)
		{
			SINGLETON = new CiApplication();
		}
		return SINGLETON;
	}

	private boolean loadProperties(Properties properties, String filename) throws Exception
	{
		InputStream is = null;
		File file = new File(filename);
		//log.debug("Looking for properties file " + filename);
		if (file.exists())
		{
			try
			{
				is = new FileInputStream(file);
				properties.load(is);
				//log.debug("Loaded properties from: " + filename);
				return true;
			}
			catch(Exception e) { ; }
			finally
			{
				if (is != null)
				{
					is.close();
				}
			}
		}
		log.debug("Didn't find or couldn't read properties file " + filename);
		return false;
	}

	/*
		Throws a RuntimeException if any of the properties are missing.
	*/
	private CiApplication() 
	{
		Properties properties = new Properties();
		final List<String> requiredProperties = 
			Arrays.asList("URL", "APPNAME", "APPID", "USERNAME", "PASSWORD") ;
		try
		{
			String propFile1 =  System.getenv("SDK_VERSIONS") + "/testdata/pycipres.conf";
			String propFile2 = System.getProperty("user.home") + File.separator + "pycipres.conf";
			String propFile3 =  System.getenv("PYCIPRES");
			loadProperties(properties, propFile1);
			loadProperties(properties, propFile2);
			if (propFile3 != null)
			{
				loadProperties(properties, propFile3);
			}
			String missingProperties = "";
			for (String key : requiredProperties)
			{
				String value = properties.getProperty(key);
				if (value == null || value.trim().length() == 0)
				{
					missingProperties += key + " ";
				}
			}
			if (missingProperties.length() > 0)
			{
				throw new Exception("Missing required properties: " + missingProperties);
			}
			restUrl = properties.getProperty("URL");
			appname = properties.getProperty("APPNAME");
			appkey = properties.getProperty("APPID"); 
			username = properties.getProperty("USERNAME");
			password = properties.getProperty("PASSWORD");
			log.info("URL=" + restUrl + 
				", APPNAME=" + appname +
				", APPID=" + appkey +
				", USERNAME=" + username );
		}
		catch (Exception e)
		{
			log.error("", e);
			throw new RuntimeException("Application Configuration error: " + e.toString());
		}
	}

	/**
	 * Gets the named property.
	 *
	 * @param name
	 *            the name
	 * @return property's value
	 */
	public String getProperty(String name)
	{
		return properties.getProperty(name);
	}

}
