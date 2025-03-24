from WriteDpv import *
from DPV import *
import os
import re
import math
import networkx as nx
from Utility_getNode import * 

class RunnerDemo:
    def __init__(self,topologyPath) -> None:
        self.dpv = Dpvnet()
        self.topologyPath = topologyPath
        self.outputPath = ""
        self.isLoop = False
        self.gragh = nx.Graph()
        
        self.setOutputPath()
        self.initGragh()
        
    def initGragh(self):
        with open(self.topologyPath, 'r') as file:
            edges = [(line.strip().split()[0], line.strip().split()[2]) for line in file.readlines()]
            print(len(edges))
            new_li=list(set(edges))
            new_li.sort(key=edges.index)
            print(len(new_li))
        
        for edge in edges:
            source, target = edge
            self.gragh.add_edge(source, target)
        
    def setOutputPath(self):
        directory = os.path.dirname(self.topologyPath)
        self.outputPath = os.path.join(directory,"DPVNet2.puml")
        
    # def getNode(self, node_name, src, dst):
    #     if node_name == src:
    #         thisNode = self.dpv.getSrc()
    #     if node_name == dst:
    #         thisNode = self.dpv.getDst()
    #     else:
    #         node_index = self.dpv.getSrc().index
    #         thisNode = Node(node_name, node_index)
            
    #     return thisNode
    
    def gen_onePair_path(self, src, dst, index):
        all_shortest_paths = list(nx.all_shortest_paths(self.gragh, source=src, target=dst))

        # 合并重复的边
        merged_path = []
        for path in all_shortest_paths:
            for i in range(len(path)-1):
                edge = (path[i], path[i+1])
                if edge not in merged_path:
                    merged_path.append(edge)
        
        
        # prevNode = Node(merged_path[0][0], index)
        # path.append(prevNode)
        
        # print(merged_path)
        
        for i in range(len(merged_path)):
            path = []
            
            Node1 = Node(merged_path[i][0], index)
            Node2 = Node(merged_path[i][1], index)
            path.append(Node1)
            path.append(Node2)
            write_path_fat(self.outputPath, path)
            
            # path.pop(0)
    
    def gen_onePair_sameDst_path(self, src, dst, index_src, index_dst):
        all_shortest_paths = list(nx.all_shortest_paths(self.gragh, source=src, target=dst))

        # 合并重复的边
        merged_path = []
        for path in all_shortest_paths:
            for i in range(len(path)-1):
                edge = (path[i], path[i+1])
                if edge not in merged_path:
                    merged_path.append(edge)
        
        
        
        for i in range(len(merged_path)):
            path = []
            if(merged_path[i][0] == dst):
                Node1 = Node(merged_path[i][0], index_dst)
            else:
                Node1 = Node(merged_path[i][0], index_src)
                
            if(merged_path[i][1] == dst):
                Node2 = Node(merged_path[i][1], index_dst)
            else:    
                Node2 = Node(merged_path[i][1], index_src)
                
            path.append(Node1)
            path.append(Node2)
            write_path_fat(self.outputPath, path)
            

    def out_one_dpvnet(self, index, src, dst, match, isStart, isEnd): #index: 当前 dpvnet 的 index
        # 首先设置 src 和 dst
        # self.outputPath = output
        self.dpv.setSandD_Demo(src,dst,index)
        
        if isStart:
            write_start(self.outputPath)
        write_head(self.outputPath, index, src, dst, match, self.dpv.getSrc().getName())
        
        self.gen_onePair_path(src, dst, index)
        
        write_last(self.outputPath, self.dpv.getDst().getName())
        
        if isEnd:
            write_end(self.outputPath)
            getAllNode(self.outputPath)
        
        print("Write to " + self.outputPath)
        
    def out_one_sameDst_dpvnet(self, indexList, srcList, dst, index ,match, isStart, isEnd):
        self.dpv.setD_Demo(dst,index)
        
        if isStart:
            write_start(self.outputPath)
        write_annotation(self.outputPath, index, srcList, dst, match)
        
        for i in range(len(srcList)):
            index_src = indexList[i]
            src = srcList[i]
            srcname = str(src) + '.' + str(index_src)
            write_path_start(self.outputPath, srcname)
            self.gen_onePair_sameDst_path(src, dst, index_src, index)
        
        write_last(self.outputPath, self.dpv.getDst().getName())
        
        if isEnd:
            write_end(self.outputPath)
            getAllNode(self.outputPath)
        
        print("Write to " + self.outputPath)
    
    def out_multi_pair_dpvnet(self, output, node_pair):
        self.outputPath = output
        
        index = 0
        isStart = True
        isEnd = False
        for i in range(len(node_pair)):
            src = node_pair[i][0]
            dst = node_pair[i][1]
            
            print(src + " -> " + dst)
                
            match = "exist >= 1"
            self.out_one_dpvnet(index, src, dst, match, isStart, isEnd)
            index += 1
            isStart = False
            
        write_end(self.outputPath)
        getAllNode(self.outputPath)
        print("Write to " + self.outputPath)
        
    def out_all_pair_dpvnet(self, output, num1, num2):
        
        self.outputPath = output
        
        allnodes = list(self.gragh.nodes)
        num_node = len(allnodes)
        
        if num1 == 0:
            num1 = num_node
        if num2 == 0:
            num2 = num_node
        
        index = 0
        isStart = True
        isEnd = False
        
        cnt1 = 0
        cnt2 = 0

        for j in range(num_node):
            dst = allnodes[j]
            if (("S2" not in dst) and ("edge" not in dst)):
                continue
            
            for i in range(num2, num_node):
                src = allnodes[i]
                if i == j or (("S2" not in src) and ("edge" not in src)):
                    continue
                
                print(src + " -> " + dst)
                
                match = "exist >= 1"
                self.out_one_dpvnet(index, src, dst, match, isStart, isEnd)
                index += 1
                isStart = False
                cnt2 += 1
                
                if cnt2 >= num1:
                    cnt2 = 0
                    break
                
            cnt1 += 1
            if cnt1 >= num2:
                break
        
        print(cnt1)
        print(cnt2)
        write_end(self.outputPath)
        
        getAllNode(self.outputPath)
        
        print("Write to " + self.outputPath)
                
                
    def out_multi_onepair_dpvnet(self, outputDir, num1, num2):
        
        allnodes = list(self.gragh.nodes)
        num_node = len(allnodes)
        
        if num1 == 0:
            num1 = num_node
        if num2 == 0:
            num2 = num_node
        
        index = 0
        
        cnt1 = 0
        cnt2 = 0

        
        
        for j in range(num_node):
            dst = allnodes[j]
            if (("S2" not in dst) and ("edge" not in dst)):
                continue
            
            for i in range(num2, num_node):
                src = allnodes[i]
                if i == j or (("S2" not in src) and ("edge" not in src)):
                    continue
                
                print(src + " -> " + dst)
                
                self.outputPath = outputDir + "/DPVNet" + str(index) + ".puml"
                match = "exist >= 1"
                self.out_one_dpvnet(index, src, dst, match, True, True)
                index += 1
                cnt2 += 1
                
                if cnt2 >= num1:
                    cnt2 = 0
                    break
                
            cnt1 += 1
            if cnt1 >= num2:
                break
        
        print(cnt1)
        print(cnt2)
        # write_end(self.outputPath)
    
    
        
    
    def out_multi_sameDst_dpvnet(self, output, num1, num2, num_group):
        
        self.outputPath = output
        
        allnodes = list(self.gragh.nodes)
        num_node = len(allnodes)
        
        if num1 == 0:
            num1 = num_node
        if num2 == 0:
            num2 = num_node
        
        index = 0
        isStart = True
        isEnd = False
        
        cnt1 = 0
        cnt2 = 0

        for j in range(num_node):
            dst = allnodes[j]
            if (("S2" not in dst) and ("edge" not in dst)):
                continue
            
            srcList = []
            srcIndex = []
            
            cnt_group = 0
            
            for i in range(num2, num_node):
                src = allnodes[i]
                if i == j or (("S2" not in src) and ("edge" not in src)):
                    continue
                
                print(src + " -> " + dst)
                srcList.append(src)
                srcIndex.append(index)
                
                index += 1
                cnt2 += 1
                cnt_group += 1
                
                if cnt_group >= num_group:
                    match = "exist >= 1"
                    self.out_one_sameDst_dpvnet(srcIndex, srcList, dst, srcIndex[0] , match, isStart, isEnd)
                    isStart = False
                    
                    cnt_group = 0
                    srcList = []
                    srcIndex = []
                    
                if cnt2 >= num1:
                    cnt2 = 0
                    break
            
            cnt1 += 1
            if cnt1 >= num2:
                break
        
        
        write_end(self.outputPath)
        getAllNode(self.outputPath)
        
        print("Write to " + self.outputPath)     
                
    def out_multi_sameDst_dpvnet_toDir(self, outputDir, num1, num2, num_group):

        allnodes = list(self.gragh.nodes)
        num_node = len(allnodes)
        
        if num1 == 0:
            num1 = num_node
        if num2 == 0:
            num2 = num_node
        
        index = 0
        isStart = True
        isEnd = False
        
        cnt1 = 0
        cnt2 = 0

        for j in range(num_node):
            dst = allnodes[j]
            if (("S2" not in dst) and ("edge" not in dst)):
                continue
            
            srcList = []
            srcIndex = []
            
            cnt_group = 0
            
            for i in range(num2, num_node):
                src = allnodes[i]
                if i == j or (("S2" not in src) and ("edge" not in src)):
                    continue
                
                print(src + " -> " + dst)
                srcList.append(src)
                srcIndex.append(index)
                
                index += 1
                cnt2 += 1
                cnt_group += 1
                
                if cnt_group >= num_group:
                    match = "exist >= 1"
                    self.outputPath = outputDir + "/DPVNet" + str(index) + ".puml"
                    self.out_one_sameDst_dpvnet(srcIndex, srcList, dst, srcIndex[0] , match, True, True)
                    
                    cnt_group = 0
                    srcList = []
                    srcIndex = []
                    
                if cnt2 >= num1:
                    cnt2 = 0
                    break
            
            cnt1 += 1
            if cnt1 >= num2:
                break
        
        
        # getAllNode(self.outputPath)
        
        