import networkx as nx
import matplotlib.pyplot as plt

# 读取文件内容
file_path = 'topology'  # 替换成你的文件路径
with open(file_path, 'r') as file:
    edges = [line.strip().split() for line in file.readlines()]

# 创建有向图并添加边
G = nx.DiGraph()
for edge in edges:
    source, port1, target, port2 = edge
    G.add_edge(source, target)
    G.add_edge(target, source)  # 添加双向边

# 画出图的初始状态
# pos = nx.spring_layout(G)
# nx.draw(G, pos, with_labels=True, node_size=700, node_color='skyblue', font_size=10, font_color='black', font_weight='bold', edge_color='gray', width=1, alpha=0.7)
# edge_labels = nx.get_edge_attributes(G)
# nx.draw_networkx_edge_labels(G, pos)
# plt.show()

# 计算两个点之间的所有最短路径
start_node = 'aggr_0_0'
end_node = 'aggr_1_0'
all_shortest_paths = list(nx.all_shortest_paths(G, source=start_node, target=end_node))

# 合并重复的边
merged_path = []
for path in all_shortest_paths:
    print(path)
    for i in range(len(path)-1):
        edge = (path[i], path[i+1])
        if edge not in merged_path:
            merged_path.append(edge)

# 打印最短路径
# for path in merged_path:
print(merged_path)
print(len(merged_path))
