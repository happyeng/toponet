#!/bin/bash

# 提取文件中的端口信息并按行排序，然后将结果输出到port_sort.txt文件中

# 检查是否提供了文件名作为参数
if [ $# -ne 1 ]; then
    echo "Usage: $0 <filename>"
    exit 1
fi

# 提取文件名
filename=$1

# 检查文件是否存在
if [ ! -f "$filename" ]; then
    echo "File not found: $filename"
    exit 1
fi

# 提取文件中的端口信息并按行排序，然后将结果输出到port_sort.txt文件中
awk -F '[{\\[\\]}]' '{print $3}' "$filename" | sort > port_sort.txt

echo "Port information sorted and saved to port_sort.txt"
