sample_checkjobs.py.txt
sample_delete.py.txt
sample_lib.py.txt
sample_submit.py.txt

The files listed above are examples of the files we generically refer to as "submit.py".  
They need to be placed somewhere on the PATH of the community user (or account that is
used to run the jobs) on each remote host that jobs will be run on.  They need to be
customized for the particular scheduler, queues, number of cores, nodes, and maximum
runtime of the host.

The tool-registry ToolResource for the host identifies the actual names of the 
scripts on the remote host.

We typically set up things like this on a remote host, in the community account:
under the home directory we have
	ngbw
		contrib
			scripts
				submit.py
				delete.py
				lib.py
				checkjobs.py
				wrappers_for_individual_tools
					(instead of calling a tool directly, the tool's pise xml file usually
					generates a command line that calls a wrapper script.  The wrapper 
					sets env vars, loads/unloads modules and may do some minor
					pre or post processing.)
			tools
				bin 
					(this directory, ~/ngbw/contrib/tools/bin is added to the PATH)
					symbolic links to current version of everything under scripts

We haven't yet developed simple example versions of these scripts.  Instead,
what you'll find here are the scripts we actually run from Cipres to submit and manage jobs
on comet.  
