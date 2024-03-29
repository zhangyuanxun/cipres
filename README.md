# CIPRES Installing Instructions:

Preliminaries:

Installation and basic operation of the application requires maven 3.0 or newer, ant, tomcat 7.0 or newer, python 2.7, java 1.8, mysql 5.5.5 or newer, iconv, zip and unzip. Also, the build process involves the installation of some python libraries, so use of virtualenv is recommended, to avoid permissions problems.


Iconv, zip and unzip are called by the included convertEncoding.sh script when a file is uploaded to the application.  The intent of convertEncoding is to take uploaded text files and convert them to the format that linux command line tools run by the portal expect. So typically it will convert line endings (for example windows or mac line endings to \n) and non ascii chars. You can modify this script, depending on the type of data your application uses.  The script tries to detect if a file is binary and not do anything.  

We typically have a dedicated account for running the application.  We log into that account to build and deploy the application.  You'll want to make sure that this user's PATH is configured such that iconv, zip and unzip can be run from the command line.  Create a python virtualenv to use when building and running the application.

This dedicated account which starts Tomcat needs write permission to the following directories:

    $CATALINA_HOME/temp/
    $CATALINA_HOME/webapps/
    $CATALINA_HOME/work/

There are some helpful permission and security tips at https://tomcat.apache.org/tomcat-7.0-doc/security-howto.html#Non-Tomcat_settings


1.) Download a copy of the source code from our GitHub repository.

    mkdir cipres
    cd cipres
    git clone https://github.com/zhangyuanxun/cipres.git source

2.) Copy static content to a web server.

3.) Create a mysql database for the application, using the schema found at source/sdk/scripts/database/cipres.sql.

4.) Create five directories that the application will use for disabled resources, scripts, logs, tool workspace, and database documents. The location of the scripts directory needs to be added to the execution path, and also needs to be assigned to the environment variable SDK_VERSIONS.

    mkdir disabled_resources scripts logs workspace db_documents
    export PATH=$PATH:/path/to/scripts
    export SDK_VERSIONS=/path/to/scripts
    cd workspace; mkdir ARCHIVE FAILED MANUAL

The disabled_resources directory is a location where one can place files that disable specific tools or specific remote compute resources. The document 
disabled_resources.txt explains this. The workspace directory is where the web application will create working
directories for jobs that are run locally. db_documents is where the files in the database will actually be stored.

5.) Create a copy of source/example, which serves as a template for new applications. During the build process, the source code for the 
application will be combined with files found in this configuration. For file that are found in both the source code and the configuration,
the version from the configuration will be preferred over that from the source code, so any customizations of the source code should be 
made to copies in the configuration, rather than the originals in the source code.

    cd source
    cp -R example my_config

6.) edit my_config/sdk/src/main/resources/ssl.properties to provide a username and password for SSH access to localhost.

7.) Edit my_config/sdk/src/main/resources/tool/tool-registry.cfg.xml to provide definitions of compute resources and custom tools.
Detailed instructions for defining compute resources and custom tools are found in toolregistry.txt in this directory. 

Each tool interface is created using an XML file that creates the web form and backend validation code at build time. Our XML is based on 
the Pasteur Institute Software Environment (PISE) XML. Detailed instructions/a reference manual for XML descriptions are found 
in this directory. XML descriptions for tool interfaces should be copied to my_config/sdk/src/main/resources/pisexml. The PISE dtd is found
there. Instructions for adding tool descriptions to the registry are found in toolregistry.txt

8.) Create a properties file for the build process, my_config/build.properties. This file will be merged with 
source/default/build.properties.template during a build, with properties found in this file replacing those found in the default file. 

The following properties are used for email notifications:

    portal.name=Portal
    email.serviceAddr=customerservice@mydomain.org
    email.adminAddr=admin@mydomain.org
    job.email.from=Web Portal
    restSupportEmail=restadmin@mydomain.org
    mailSender=ucsd.mailSender
    email.smtpServer=smtp.sdsc.edu
    email.smtpServer.port=25

If you don't want email notifications sent to users, you should set job.email.enable to false. The URL for the static web content from step 2
should be assigned to this property:

    build.portal.staticUrl=http://myserver.mydomain.org

Add the following to let the build know where your Tomcat is installed...if you have the CATALINA_HOME environment set, add:

    build.portal.tomcatDir=@{env.CATALINA_HOME}

otherwise, add:

    build.portal.tomcatDir=/path/to/your/tomncat

Add the following to specify the sub-folder portion of the portal's URL.  If undefined, by default, it will be "cipresportal":

    build.portal.appName=your_choice

So the portal's full URL will be http://your.server:8080/your_choice

The following properties refer to the directories created in step 4:

    workspace=/path/to/workspace
    disabled.resources.file=/path/to/disabled_resources
    logs=/path/to/logs
    database.fileRoot=/path/to/db_documents
    build.sdk.versionsDir=/path/to/scripts

The username and password that will be used for authentication to the database server should be assigned to these properties:

    database.username=
    database.password=

If you'd like to use the REST service, you'll need to provide a username and password for an administrator account:

    admin.username=
    admin.password=

9.) Edit source files in my_config/portal/src/main/webapp to provide a title for the application, custom home page text, etc. 
Image files should be copied to my_config/portal/src/main/webapp/images. Two images files in particular that might want to provide 
are a favorite icon, favicon.ico, and the main logo for the site's page header, logo.gif. The intended dimensions of the logo are 
486 by 106 pixels.

10.) Build and deploy. The script recognizes four targets: clean, prepare, package, and deploy, with the deploy target implicitly 
invoking prepare and package.

    ./build.py --conf-dir=my_config deploy

By default, the build script will build the application as a web portal. For building both the web portal and the REST service, you'll
need to use the following command:

    ./build.py --conf-dir=my_config --module=portal --module=rest deploy

11.) If you're using the REST service, you'll need to create an administrator account for it. Note that the credentials should be the same
as those given in the build properties file in step 8.

     cipresAdmin -u username -p password -e email@mydomain.org


RUNNING ON REMOTE COMPUTE HOSTS:

First set up public key ssh authentication from the web application to the compute host.  For example if you're running the web application
as user "webuser" on a machine named "webhost" and you'll be running jobs as the user "computeuser" on a cluster named
"computehost", make sure you can log into webuser@webhost and run "ssh computeuser@computehost" without being prompted 
for a password or other authentication.  The application will be communicating with the remote host the same way.

It's also possible to use globus gsissh but that is not explained here (yet).

See remote_hosts.txt in this directory for the detailed instructions on the next steps for adding compute hosts.


CRON JOBS:
There are a number of scripts in sdk/scripts that are normally configured to run from cron.  For example submitJobD, loadResultsD
and checkjobsD are daemons that are built as part of the sdk build and are used to submit jobs, retrieve results and check status
of processes running on remote hosts.  These daemons may be run from cron or initd or similar.   Another script, usage.sh, which
is documented in sdk/scripts/Readme_usage.txt, gathers XSEDE accounting info (which that may or may not be relevant to a particular
framework installation).
