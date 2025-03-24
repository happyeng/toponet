#!/bin/bash

# 检查参数数量
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <file1> <file2>"
    exit 1
fi

FILE1=$1
FILE2=$2

# 检查文件是否存在
if [ ! -f "$FILE1" ]; then
    echo "Error: File $FILE1 does not exist."
    exit 1
fi

if [ ! -f "$FILE2" ]; then
    echo "Error: File $FILE2 does not exist."
    exit 1
fi

# 比较两个文件
diff_output=$(diff "$FILE1" "$FILE2")

# 输出比较结果
if [ -z "$diff_output" ]; then
    echo "The files are identical."
else
    echo "The files have differences:"
    echo "$diff_output"
fi
