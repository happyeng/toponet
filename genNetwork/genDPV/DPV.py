class Node:
    def __init__(self) -> None:
        pass
    
    def __init__(self, device, index) -> None:
        self.device = device
        self.index = index
        self.nextNode = []
        
    def setName(self, device, index):
        self.device = device
        self.index = index
        
    def addNeighbor(self, nbr):
        self.nextNode.append(nbr)
    
    def getNext(self):
        return self.nextNode
    
    def getName(self):
        name = str(self.device) + '.' + str(self.index)
        return name
        
    def writeDpvToFile(self, outputfile):
        with open(outputfile,'a') as ofile:
            name1 = self.getName()
            for next in self.nextNode:
                name2 = next.getName()
                edge = name1 + '-->' + name2 + ':[[]]\n'
                ofile.write(edge)

class AllDpvnet:
    def __init__(self) -> None:
        self.nodeList = []
        self.deviceIndexList = {}  # 字典 存储device最大的index
    
_NAME = "DPVNet"
class Dpvnet:
    def __init__(self) -> None:
        self.nodeList = []
        # self.numNode = 0
        self.deviceIndexList = {}  # 字典 存储device最大的index
        self.src = ''
        self.dst = ''
    
    # 获得最后一个node的index
    def getFinalNodeIndex(self,nodeDevice):
        if nodeDevice not in self.deviceIndexList:
            self.deviceIndexList[nodeDevice] = 0
        else:
            self.deviceIndexList[nodeDevice] += 1
        
        return self.deviceIndexList[nodeDevice]
        
    def addNode(self,device,index):
        newNode = Node(device,index)
        self.nodeList.append(newNode)
    
    def output_puml(self, states_pair: list, output, hide_label=False):
        with open(output, mode="w", encoding="utf-8") as f:
            f.write("@startuml\n")
            count = 0
            for packet_space, ingress, match, path_exp, states in states_pair:
                self.mark_state_index(states)
                f.write("state %s%d {\n" % (_NAME, count))
                f.write("'packet_space:%s\n" % packet_space)
                f.write("'ingress:%s\n" % ingress)
                f.write("'match: %s\n" % match)
                f.write("'path: %s\n" % path_exp)
                for state in states:
                    for e in state.edge_out:
                        f.write("%s-->%s:%s\n" % (state.get_name(), e.dst.get_name(), e.get_label()))
                    if state.is_accept:
                        f.write("%s-->[*]:[[]]\n" % (state.get_name()))
                f.write("}\n")
                count += 1

            f.write("@enduml\n")
            
    def setSandD(self,src,dst):
        self.src = Node(src, self.getFinalNodeIndex(src))
        self.dst = Node(dst, self.getFinalNodeIndex(dst))
        
    def setSandD_Demo(self,src,dst, index):
        self.src = Node(src, index)
        self.dst = Node(dst, index)
    
    def setD_Demo(self,dst, index):
        self.dst = Node(dst, index)
        
    def getSrc(self):
        return self.src

    def getDst(self):
        return self.dst