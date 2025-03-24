#!/bin/bash

# 启动 Java 程序并获取其进程 ID，并将输出重定向到临时文件
cd /home/tiger/dfy/dpv/dpv-pp/topo/ddpv-runByTopo ; 
/usr/bin/env /home/tiger/pp/java/jdk22/usr/lib/jvm/jdk-22-oracle-x64/bin/java @/tmp/cp_c6uh9gr5rf6rmab473c96oe6a.argfile Main cbs fattree48 --show_result --use_OneThreadOneDpvnet --thread_pool_size 220 > temp_output.txt 2>&1 &

PID=$!
echo "Java PID: $PID"

topo="fattree80"

# 等待 temp_output.txt 文件生成
while [ ! -f temp_output.txt ]; do
    sleep 1
done

# 定义 perf options，这里使用 cache-references 和 cache-misses 事件
PERF_OPTIONS="-e cache-references,cache-misses"

# 启动 perf stat 监测 Java 程序，并将输出重定向到 cpu_cache.txt 文件中
perf stat -p $PID $PERF_OPTIONS &> perf_output.txt

# 提取缓存命中次数和引用次数
CACHE_REFERENCES=$(grep "cache-references" perf_output.txt | awk '{print $1}')
CACHE_MISSES=$(grep "cache-misses" perf_output.txt | awk '{print $1}')

# 计算缓存命中率（整数计算）
CACHE_HIT_RATE=$((100 * ($CACHE_REFERENCES - $CACHE_MISSES) / $CACHE_REFERENCES))

# 输出结果到文件
echo "缓存引用次数：$CACHE_REFERENCES" > cpu_cache.txt
echo "缓存未命中次数：$CACHE_MISSES" >> cpu_cache.txt
echo "缓存命中率：$CACHE_HIT_RATE%" >> cpu_cache.txt

echo "perf stat output saved to cpu_cache.txt"
