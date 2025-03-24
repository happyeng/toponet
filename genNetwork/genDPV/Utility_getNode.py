import re

def extract_nodes_from_puml(puml_content):
    nodes = set()
    pattern = re.compile(r'(\w+\d+)\.\d+|-->(\w+\d+)\.\d+')

    matches = pattern.findall(puml_content)

    for match in matches:
        if match[0] != "":
            nodes.add(match[0])  # 添加连接线前的节点
        if match[1] != "":
            nodes.add(match[1])  # 添加连接线后的节点

    return nodes

# 从文件中读取PlantUML内容
topo = "fattree4"
input_file_path = "../../config/demo_n/DPVNet5.puml"

def getAllNode(input_file_path):
    with open(input_file_path, "r") as file:
        puml_content = file.read()

    result = extract_nodes_from_puml(puml_content)
    # print(result)

    # 将节点写入新文件
    output_file_path =  input_file_path.replace(".puml","") + "_nodes.txt"
    with open(output_file_path, "w") as output_file:
        for node in result:
            output_file.write(node + "\n")

    print("Nodes have been written to:", output_file_path)
    
    return len(result)
