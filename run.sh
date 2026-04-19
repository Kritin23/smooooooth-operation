if [ $# -ne 1 ]; then 
    echo "Provide testcase"
    exit 1
fi


DIR=testcases/$1

rm -rf sootOutput/$1
rm -rf sootJimple/*

mkdir -p sootOutput/$1
mkdir -p sootJimple

rm *.class
javac testcases/$1/Test.java

echo "----- Baseline Output-----"
java -Xint -cp $DIR Test | tee baseline.out


javac -g -cp .:soot.jar PA4.java
java -cp .:soot.jar PA4 $1 > out.log

echo "----- Optimized Output -----"
java -Xint -cp sootOutput/$1 Test | tee optimized.out

