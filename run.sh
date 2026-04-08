if [ $# -ne 1 ]; then 
    echo "Provide testcase"
    exit 1
fi

rm sootOutpupt/*
javac testcases/$1/Test.java
javac -cp .:soot.jar PA4.java
java -cp .:soot.jar PA4 $1
