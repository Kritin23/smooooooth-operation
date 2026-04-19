echo "" > results.txt

for test in $(ls testcases); do 
    echo $test | tee -a results.txt 
    ./run.sh $test | tee -a results.txt
    echo "----------------------------------------" | tee -a results.txt
done