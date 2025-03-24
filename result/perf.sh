#!/bin/bash

# 启动 Java 程序并获取其进程 ID，并将输出重定向到临时文件
cd /home/tiger/dfy/dpv/dpv-pp/topo/ddpv-runByTopo ;
/usr/bin/env /usr/lib/jvm/java-11-byteopenjdk-amd64/bin/java @/tmp/cp_6rf4o9i88o6ryios7cbf8jiij.argfile Main cbs lfrz2/ipv4 --show_result --thread_pool_size 256 > temp_output.txt 2>&1 &

PID=$!
echo $PID

topo="fattree80"

# 等待 temp_output.txt 文件生成
while [ ! -f temp_output.txt ]; do
    sleep 1
done

# 定义输出信息
START_MESSAGE="Start Build in Runner!!!"
END_MESSAGE="node, device, dvnetBDD转化所花费的总时间"
END_START="End Build in Runner!!"
TEST_SIGNAL="total time"

# 等待启动信息并执行 perf stat
while true; do
    if grep -q "$START_MESSAGE" temp_output.txt; then
        sleep 1
        echo $PID
        perf stat -p $PID &> perf_output.txt &
        PERF_PID=$!
        break
    fi
    sleep 1
done

current_path=$(pwd)  
echo "当前路径是：$current_path"



# 循环检查结束信息并重新启动 perf stat
while true; do
    if grep -q "$END_START" temp_output.txt; then
        kill -SIGINT $PERF_PID  # 发送 SIGINT 信号终止 perf stat 进程

        # 将 perf stat 输出存储到日志文件中
        # cat perf_output.txt >> cpu_utilization_log.txt
        sleep 1
        echo $PID
        perf stat -p $PID &> perf_output2.txt &
        PERF_PID=$!

        bash ./result/cal_cpu.sh $PID
        cal_PID=$!
        echo $cal_PID
        break
    fi
done

while true; do
    if grep -q "$TEST_SIGNAL" temp_output.txt; then
        kill -SIGINT $PERF_PID  # 发送 SIGINT 信号终止 perf stat 进程
        wait $PERF_PID
        kill -SIGINT $cal_PID

        break
    fi
done


# 等待程序结束
wait $PID

# 存储进程ID到日志文件
echo "$topo" >> cpu_utilization_log.txt
# 提取 perf_output2.txt 中的 /CPUs utilized/ {print $5} 到 cpu_utilization_log.txt
awk '/CPUs utilized/ {printf $5}' perf_output.txt >> cpu_utilization_log.txt
echo -n  " " >> cpu_utilization_log.txt
# 提取 perf_output2.txt 中的 /CPUs utilized/ {print $5} 到 cpu_utilization_log.txt
awk '/CPUs utilized/ {printf $5}' perf_output2.txt >> cpu_utilization_log.txt
echo " " >> cpu_utilization_log.txt

# 删除临时文件
rm temp_output.txt perf_output.txt perf_output2.txt


