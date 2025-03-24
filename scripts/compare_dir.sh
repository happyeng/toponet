#!/bin/bash

# 检查参数数量
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <directory1> <directory2>"
    exit 1
fi

DIR1=$1
DIR2=$2

# 检查目录是否存在
if [ ! -d "$DIR1" ]; then
    echo "Error: Directory $DIR1 does not exist."
    exit 1
fi

if [ ! -d "$DIR2" ]; then
    echo "Error: Directory $DIR2 does not exist."
    exit 1
fi

# 比较两个目录内的所有文件
diff_output=$(diff -qr "$DIR1" "$DIR2")

# 输出比较结果
if [ -z "$diff_output" ]; then
    echo "The directories are identical."
else
    echo "The directories have differences:"
    echo "$diff_output"
fi
