#!/bin/sh

# SEE ALSO:
# cipres-rest-service/pycipres/testdata/tooltests/runtooltest
# limit.sh


if [[ $# < 2 ]] ; then
	echo "Cipres Admin username and pass required on commandline." 
	exit 1
fi


echo "========================================================================================="
echo "=====================================RUN job.sh=============================================="
echo "========================================================================================="
sh job.sh $1 $2 

echo "========================================================================================="
echo "=====================================RUN job2.sh=============================================="
echo "========================================================================================="
sh job2.sh $1 $2

echo "========================================================================================="
echo "=====================================RUN user.sh=============================================="
echo "========================================================================================="
sh user.sh $1 $2

echo "========================================================================================="
echo "=====================================RUN user2.sh=============================================="
echo "========================================================================================="
sh user2.sh $1 $2

echo "========================================================================================="
echo "=====================================RUN submit.sh=============================================="
echo "========================================================================================="
sh submit.sh $1 $2

echo "========================================================================================="
echo "=====================================RUN app.sh=============================================="
echo "========================================================================================="
sh app.sh $1 $2 

echo "========================================================================================="
echo "=====================================RUN url2.sh=============================================="
echo "========================================================================================="
sh url2.sh $1 $2 

echo "========================================================================================="
echo "=====================================RUN tus_direct.sh==================================="
echo "========================================================================================="
# sh tus_direct.sh $1 $2 

echo "========================================================================================="
echo "=====================================RUN tus_umbrella.sh================================="
echo "========================================================================================="
# sh tus_umbrella.sh $1 $2 


