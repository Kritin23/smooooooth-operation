if [ $# -ne 1 ]; then 
    echo "Provide testcase"
    exit 1
fi


DIR=testcases/$1

rm -rf src/sootOutput/$1
rm -rf src/sootJimple/*

mkdir -p src/sootOutput/$1
mkdir -p src/sootJimple

rm *.class
javac testcases/$1/Test.java

echo "----- Baseline Output-----"
java -Xint -cp $DIR Test

cd src

javac -g -cp .:../soot.jar Main.java
java -cp .:../soot.jar Main $1 > out.log

echo "----- Optimized Output -----"
java -Xint -cp sootOutput/$1 Test

cd ..

