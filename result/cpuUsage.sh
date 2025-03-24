#!/bin/bash

# 启动 Java 程序并获取其进程 ID，并将输出重定向到临时文件
cd /home/tiger/dfy/dpv/dpv-pp/topo/ddpv-runByTopo ; 
/home/tiger/pp/java/jdk22/usr/lib/jvm/jdk-22-oracle-x64/bin/java @/tmp/cp_c6uh9gr5rf6rmab473c96oe6a.argfile Main cbs fattree48 --show_result --use_OneThreadOneDpvnet --thread_pool_size 220 > temp_output.txt 2>&1 &

JAVA_PID=$!
echo "Java PID: $JAVA_PID"

# 等待 temp_output.txt 文件生成
while [ ! -f temp_output.txt ]; do
    sleep 1
done

# 定义 perf options，这里使用 instructions 和 cycles 事件
PERF_OPTIONS="-e instructions,cycles"

# 启动 perf stat 监测 Java 程序，并将输出重定向到 perf_output.txt 文件中
perf stat -p $JAVA_PID $PERF_OPTIONS &> perf_output.txt

# 提取 perf 输出中的 instructions 和 cycles 数值
INSTRUCTIONS=$(grep "instructions" perf_output.txt | awk '{print $1}')
CYCLES=$(grep "cycles" perf_output.txt | awk '{print $1}')

# 计算 CPU 使用率（整数计算）
CPU_USAGE=$((100 * $INSTRUCTIONS / $CYCLES))

# 输出结果到文件
echo "Java PID: $JAVA_PID" > cpu_usage.txt
echo "Instructions: $INSTRUCTIONS" >> cpu_usage.txt
echo "Cycles: $CYCLES" >> cpu_usage.txt
echo "CPU Usage: $CPU_USAGE%" >> cpu_usage.txt

echo "perf stat output saved to cpu_usage.txt"
