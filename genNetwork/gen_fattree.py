import math

def generate_fattree_topology(k):
    topology = []
    total_edges = 0
    num_core = int((k/2)**2)

    # core-agg 连接数字大的agg
    for i in range(num_core):
        core_switch = f"core_{i}"
        agg_index = int(math.floor(i/(k/2)) + k/2)
        for j in range(k):
            aggregation_switch = f"aggr_{j}_{agg_index}"
            total_edges += 1
            topology.append((core_switch, f"{core_switch}->{aggregation_switch}", aggregation_switch, f"{aggregation_switch}->{core_switch}"))

    # agg-edge
    for pod_index in range(k):
        for j in range(int(k/2)):
            aggregation_switch1 = f"aggr_{pod_index}_{int(j + k/2)}"
            for m in range(int(k/2)):
                aggregation_switch2 = f"aggr_{pod_index}_{m}"
                total_edges += 1
                topology.append((aggregation_switch1, f"{aggregation_switch1}->{aggregation_switch2}", aggregation_switch2, f"{aggregation_switch2}->{aggregation_switch1}"))
    
    # edge-host
    # for pod_index in range(k):
    #     for j in range(int(k/2)):
    #         agg_index = j
    #         aggregation_switch = f"aggr_{pod_index}_{agg_index}"
    #         for m in range(int(k/2)):
    #             host_index = int(m + j*k/2)
    #             host_switch = f"host_{pod_index}_{host_index}"
    #             topology.append((host_switch, f"{host_switch}->{aggregation_switch}", aggregation_switch, f"{aggregation_switch}->{host_switch}"))
    #             total_edges += 1
                
    return topology, total_edges

def save_topology_to_file(topology, filename):
    with open(filename, 'w') as file:
        for edge in topology:
            file.write(" ".join(edge) + "\n")

# Example usage
k_value = 90  # You can adjust this based on your desired Fattree size
fattree_topology, total_edges = generate_fattree_topology(k_value)
save_topology_to_file(fattree_topology, '../config/fattree' + str(k_value) + '/topology')

print(f"Total edges in Fattree topology: {total_edges}")
k = k_value
total_nodes = 5*k*k/4
print(f"Total nodes in Fattree topology: {total_nodes}")