from WriteDpv import *
from DPV import *
import os
import re
import math

class RunnerFattree:
    def __init__(self,kvalue) -> None:
        self.dpv = Dpvnet()
        self.outputPath = ""
        self.isLoop = False
        self.kvalue = kvalue
        self.setOutputPath()
    
    def setOutputPath(self):
        self.outputPath = "../../config/fattree" + str(self.kvalue) + "/DPVNet1.puml"
    
    def gen_onePair_path(self, src, dst):
        path = []
        path.append(self.dpv.getSrc())
        
        h_k = int(self.kvalue/2)
        if "edge" in src and "edge" in dst:
            match = re.match(r"edgei(\d+)i(\d+)", src)
            if match:
                src_pod = int(match.group(1))
                src_index = int(match.group(2))
            
            match = re.match(r"edgei(\d+)i(\d+)", dst)
            if match:
                dst_pod = int(match.group(1))
                dst_index = int(match.group(2))
                
            if src_pod == dst_pod: # 在同一个 pod 中
                for i in range(h_k):
                    path = []
                    path.append(self.dpv.getSrc())
                    
                    node_name = "aggri" + str(src_pod) + "i" + str(i)
                    node_index = self.dpv.getFinalNodeIndex(node_name)
                    node = Node(node_name, node_index)
                    path.append(node)
                    path.append(self.dpv.getDst())
                    
                    write_path_fat(self.outputPath, path)
            else:
                for i in range(h_k):
                    path = []
                    path.append(self.dpv.getSrc())
                    
                    node_name = "aggri" + str(src_pod) + "i" + str(i)
                    node_index = self.dpv.getFinalNodeIndex(node_name)
                    src_pod_anode = Node(node_name, node_index)
                    path.append(src_pod_anode)
                    
                    write_path_fat(self.outputPath, path) # 写入 src pod 中传递
                    
                    dst_agg = "aggri" + str(dst_pod) + "i" + str(i)
                    node_index = self.dpv.getFinalNodeIndex(dst_agg)
                    dst_pod_anode = Node(dst_agg, node_index)
                    
                    for j in range(h_k):
                        path = []
                        path.append(src_pod_anode)
                        
                        core_name = "corei" + str(j+i*h_k)
                        node_index = self.dpv.getFinalNodeIndex(core_name)
                        node = Node(core_name, node_index)
                        path.append(node)
                        
                        
                        path.append(dst_pod_anode)
                        write_path_fat(self.outputPath, path)
                    
                    path = []
                    path.append(dst_pod_anode)
                    path.append(self.dpv.getDst())
                    write_path_fat(self.outputPath, path)
                    

    def out_one_dpvnet(self, index, src, dst, match, isStart, isEnd): #index: 当前 dpvnet 的 index
        # 首先设置 src 和 dst
        
        self.dpv.setSandD(src,dst)
        
        if isStart:
            write_start(self.outputPath)
        write_head(self.outputPath, index, src, dst, match, self.dpv.getSrc().getName())
        
        self.gen_onePair_path(src, dst)
        
        write_last(self.outputPath, self.dpv.getDst().getName())
        
        if isEnd:
            write_end(self.outputPath)
        
        print("Write to " + self.outputPath)
        
        
    def out_all_pair_dpvnet(self, output):
        self.outputPath = output
        h_k = int(self.kvalue/2)
        num_node = h_k * self.kvalue
        index = 0
        isStart = True
        isEnd = False
        for i in range(num_node):
            src_pod = math.floor(i/h_k)
            src_index = i - src_pod*h_k
            src = "edgei" + str(src_pod) + "i" + str(src_index)
            for j in range(num_node):
                if i == j:
                    continue
                dst_pod = math.floor(j/h_k)
                dst_index = j - dst_pod*h_k
                dst = "edgei" + str(dst_pod) + "i" + str(dst_index)
                
                print(src + " -> " + dst)
                
                match = "exist >= 1"
                if index == (num_node*(num_node - 1) -1):
                    isEnd = True
                self.out_one_dpvnet(index, src, dst, match, isStart, isEnd)
                index += 1
                isStart = False
        
        print("Write to " + self.outputPath)
                
                