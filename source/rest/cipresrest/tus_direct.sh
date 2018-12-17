#!/bin/bash

# Tests umbrella auth with tus_servlet, assumes tus_servlet is at $URL/tmpfiles

if [[ $# < 2 ]] ; then
	echo "cipresadmin username and pass required on commandline"
	exit 1
fi

# testfw.sh defines some functions and gets env vars from pycipres.conf
source "./testfw.sh" 

#  These come from pycipres.conf
APP1=$PYCIPRES_APP
USER1=$PYCIPRES_EU
APP2=$PYCIPRES2_APP
USER2=$PYCIPRES_EU2
PASS=$TESTPASS

# This is set in testfw.sh/config
APPKEY1=$PYCIPRES_APPKEY
APPKEY2=$PYCIPRES2_APPKEY


# For the tus upload
SIZE=2299
FILE="testdata.txt"
FILENAME_BASE="testdata"
FILENAME="$(echo testdata.txt | base64)"
TUS_URL="$URL/tmpfiles"
FILE_API="$URL/file"

# Upload a file, returns 0 on success.  Sets TUS_FILE_URL
upload()
{
	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		-H 'Tus-Resumable: 1.0.0' \
		-H "Upload-Length: $SIZE" \
    	-H "Upload-Metadata: filename $FILENAME"
		-X POST $TUS_URL 
	)
	if ! shouldpass command "Location" Location: ; then
		echo "TEST FAILED"
		return 1	
	fi
	TUS_FILE_URL=$EXTRACTED
	echo "file_url is $TUS_FILE_URL"

	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		-H 'Tus-Resumable: 1.0.0' \
		-H 'Upload-Offset: 0' \
		-H 'Content-Type: application/offset+octet-stream' \
		--upload-file $FILE \
		-X PATCH $TUS_FILE_URL 
	)

	if ! shouldpass command "Upload-Offset" Upload-Offset: ; then
		echo "TEST FAILED"
		return 1
	fi

	OFFSET=$EXTRACTED
	echo "offset is $OFFSET"
	if (( $OFFSET != $SIZE )) ; then
		echo "TEST FAILED : $OFFSET != $SIZE "
		return 1
	fi
	return 0
}

# Import from $TUS_FILE_URL, returns 0 on success.  Sets FILE_URL
uploadAndImport()
{
	upload
	if [[ $? != 0 ]]; then 
		return $?
	fi
	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		$FILE_API \
		-F sourceUri=$TUS_FILE_URL
	)
	if ! shouldpass command "url" url ; then
		echo "TEST FAILED"
		return 1
	fi
	FILE_URL=$EXTRACTED
	return 0 
}


# Do an upload, test wrong user trying to import
Atest1()
{
	error=0

	echo "\n=================================================================="
	echo "Atest1a: do an upload for user $USER1"
	echo "=================================================================="
	upload
	if [[ $? != 0 ]]; then 
		return $?
	fi

	echo "\n=================================================================="
	echo "Atest1c: wrong user, $USER2 , tries to import"
	echo "=================================================================="

	command=(curl -i -k -u "$USER2:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		$FILE_API \
		-F sourceUri=$TUS_FILE_URL
	)
	if ! shouldfail command "AuthorizationException|AuthenticationException" ; then
		echo "TEST FAILED"
		error=1
	fi

	return $error
}


# Do an upload, do a GET of the TUS url, import, try to import again (should fail)
Atest2()
{
	error=0
	echo "\n=================================================================="
	echo "Atest2a: do an upload for user $USER1"
	echo "=================================================================="
	upload
	if [[ $? != 0 ]]; then 
		return $?
	fi

	echo "\n=================================================================="
	echo "Atest2c: Do a GET of $TUS_FILE_URL .  No credentials needed."
	echo "=================================================================="

	# command=(curl -i -k -u "$USER1:$PASS" \
		# -H "cipres-appkey:$APPKEY1"  \
		# -H 'Tus-Resumable: 1.0.0' \
		# $TUS_FILE_URL 
	# )

	command=(curl -i -k -H 'Tus-Resumable: 1.0.0' $TUS_FILE_URL  )

	if ! shouldpass command "username=" ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest2d: import the upload for user $USER1"
	echo "=================================================================="

	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		$FILE_API \
		-F sourceUri=$TUS_FILE_URL
	)
	if ! shouldpass command ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest2d: import the upload for user $USER1 AGAIN.  SHOULD FAIL"
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		$FILE_API \
		-F sourceUri=$TUS_FILE_URL
	)
	if ! shouldfail command ; then
		echo "TEST FAILED"
		error=1
	fi

	return $serror
}


# upload, then test serveral import error conditions:
# 	missing source parameter, malformed source url, non-tus source url, tus url with non-existant uuid
Atest3()
{
	error=0
	echo "\n=================================================================="
	echo "Atest3a: do an upload for user $USER1"
	echo "=================================================================="
	upload
	if [[ $? != 0 ]]; then 
		return $?
	fi

	echo "\n=================================================================="
	echo "Atest3c: import the upload but omit the source parameter.  SHOULD FAIL"
	echo "=================================================================="

	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		-X POST
		$FILE_API 
	)
	if ! shouldfail command ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest3d: import the upload with a bad TUS source URL.  SHOULD FAIL"
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		$FILE_API \
		-F sourceUri=$TUS_URL/foobar
	)
	if ! shouldfail command ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest3e: omit source param, include a bogus param.  SHOULD FAIL"
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" \
		-H "cipres-appkey:$APPKEY1"  \
		$FILE_API \
		-F foo=bar
	)
	if ! shouldfail command ; then
		echo "TEST FAILED"
		error=1
	fi

	return $error
}

# Upload, import and GET, then some GET error cases
Atest4()
{
	error=0

	echo "\n=================================================================="
	echo "Atest4a: upload and import" 
	echo "=================================================================="

	uploadAndImport
	if [[ $? != 0 ]]; then 
		return $?
	fi
	echo "FILE_URL is $FILE_URL"

	echo "\n=================================================================="
	echo "Atest4a: GET the data item info" 
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" -H "cipres-appkey:$APPKEY1"  $FILE_URL )
	if ! shouldpass command "dataitem"; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest4b: GET non-existant data item. SHOULD FAIL " 
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" -H "cipres-appkey:$APPKEY1"  $FILE_API/this_is_garbage )
	if ! shouldfail command "Not Found" ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest4c: GET as wrong user. SHOULD FAIL " 
	echo "=================================================================="
	command=(curl -i -k -u "$USER2:$PASS" -H "cipres-appkey:$APPKEY1"  $FILE_URL)
	if ! shouldfail command "Auth" ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest4d: GET without credentials. SHOULD FAIL " 
	echo "=================================================================="
	command=(curl -i -k  -H "cipres-appkey:$APPKEY1"  $FILE_URL)
	if ! shouldfail command "Auth" ; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest4e: GET the data item info correctly again." 
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" -H "cipres-appkey:$APPKEY1"  $FILE_URL )
	if ! shouldpass command "dataitem"; then
		echo "TEST FAILED"
		error=1
	fi

	echo "\n=================================================================="
	echo "Atest4f: Download the data.  Note that filename that isn't unique" 
	echo "in folder may be renamed by cipres with _n appended before the file"
	echo "extension, making it difficult to check filename in content header."
	echo "=================================================================="
	command=(curl -i -k -u "$USER1:$PASS" -H "cipres-appkey:$APPKEY1"  $FILE_URL?alt=media )
	if ! shouldpass command "filename=$FILENAME_BASE"; then
		echo "TEST FAILED"
		error=1
	fi

	return $error
}










runtests Atest1 Atest2 Atest3 Atest4

