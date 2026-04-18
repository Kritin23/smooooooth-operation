if [ $# -ne 1 ]; then 
    echo "Provide testcase"
    exit 1
fi

rm sootOutpupt/*
rm *.class
javac testcases/$1/Test.java
javac -g -cp .:soot.jar PA4.java
jdb -classpath .:soot.jar PA4 $1
