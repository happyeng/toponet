#! /bin/bash




# if [ -f filename];then
#   rm -r filename
# fi


pid=$1
interval=5

echo "cal_cpu：$pid"

# 指定输出文件
output_file="record700.txt"

while true; do
  # 获取进程的内存使用情况（以百分比为单位）
  memory_usage_percentage=$(ps -o %mem= -p "$pid")

  # 获取进程的CPU占用情况（以百分比为单位）
  cpu_usage=$(top -b -n 1 -p "$pid" | grep "$pid" | awk '{print $9}')
  # 获取进程的CPU占用情况（以百分比为单位）
  cpu_percentage=$(ps -p "$pid" -o %cpu | tail -n 1)

  # 将结果追加到文件（每秒一行）
  echo "$(date) $process_name $pid ${memory_usage_percentage}% ${cpu_usage}%" >> "$output_file"

#     # 获取进程的CPU占用情况（以百分比为单位）
#   cpu_percentage=$(top -b -n 1 -p "$pid" | grep "$pid" | awk '{print $9}')

#   # 获取进程的内存使用情况（以百分比为单位）
#   mem_percentage=$(top -b -n 1 -p "$pid" | grep "$pid" | awk '{print $10}')

#   # 打印结果
#   echo "$(date) PID: $pid CPU: ${cpu_percentage}%, Mem: ${mem_percentage}%" >> "$output_file"

  # 等待1秒
  sleep $interval
done


# 这一行永远不会执行，因为循环是无限的
exit 0
