from Network import Port
from DPV import *
import networkx as nx
import os
from WriteDpv import *

class Runner:
    def __init__(self,filename) -> None:
        self.network = nx.Graph()
        self.dpv = Dpvnet()
        self.outputPath = ""
        self.isLoop = False
        
        # 获得topo结构
        self.read_topology_from_file(filename)
        
        # self.ports = {} # device:[Port()]
        
    def add_edge(self, device1, port1, device2, port2):
        if device1 not in self.network.nodes:
            self.network.add_node(device1)
            self.network.nodes[device1]['port'] = {}
        if device2 not in self.network.nodes:
            self.network.add_node(device2)
            self.network.nodes[device2]['port'] = {} 

        self.network.add_edge(device1,device2)
        self.network.add_edge(device2,device1)
        # self.network.add_edge(device2,device1)
        p1 = Port(device1, port1)
        p2 = Port(device2, port2)
        p1.link(p2)
        self.network.nodes[device1]['port'].setdefault(device2, set()).add(p2)
        self.network.nodes[device2]['port'].setdefault(device1, set()).add(p1)
    
    def getNetwork(self):
        return self.network
    
    def read_topology_from_file(self, filename):
        self.setOutputPath(filename)
        with open(filename, mode="r") as f:
            line = f.readline()
            while line:
                token = line.strip().split(" ")

                if len(token) == 4:
                    self.add_edge(token[0], token[1], token[2], token[3])
                elif len(token) == 2:
                    self.add_edge(token[0], token[0] + '->' + token[1], token[1], token[1] + '->' + token[0])
                else:
                    print(str(token) + " can not parsed")
                line = f.readline()
    
    def setOutputPath(self, original_path):
        base_path = os.path.dirname(original_path)
        self.outputPath = os.path.join(base_path, "DPVNet1.puml")
    
    def dfs(self,src, dst, cur, repath:list, pathName:list):
        if cur == dst:
            if self.isLoop and len(repath) == 0: # 起点
                node = self.dpv.getSrc()
            else:
                node = self.dpv.getDst()
                pathName.append(cur)
                repath.append(node)
                write_path(self.outputPath, repath)
                return    
        elif cur == src:
            if self.isLoop and len(repath) != 0: # 终点
                node = self.dpv.getDst()
            else:
                node = self.dpv.getSrc()
        else:
            index = self.dpv.getFinalNodeIndex(cur)
            node = Node(cur, index)
            
        pathName.append(cur)
        repath.append(node)
        
        for n in self.network[cur]:
            if n not in pathName:
                self.dfs(src, dst, n, repath, pathName)
                repath.pop()
                pathName.pop()
            else:
                if self.isLoop and n == src and len(repath) != 0:
                    self.dfs(src, dst, n, repath, pathName)
                    
        
    def out_one_dpvnet(self, index, src, dst, match): #index: 当前 dpvnet 的 index
        # 首先设置 src 和 dst
        self.dpv.setSandD(src,dst)
        
        write_start(self.outputPath)
        write_head(self.outputPath,index, src, dst, match, self.dpv.getSrc().getName())
        
        
        self.dfs(src,dst,src,[],[])
        
        write_last(self.outputPath, self.dpv.getDst().getName())
        write_end(self.outputPath)
        
        print("Write to " + self.outputPath)