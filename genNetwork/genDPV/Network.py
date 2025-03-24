class Port:
    def __init__(self, device, name):
        self.device = device
        self.name = name
        self.to = None

    def link(self, port):
        self.to = port
        port.to = self

    def __hash__(self):
        return hash(self.device+self.name)

    def __eq__(self, other):
        return str(self) == str(other)

    def __str__(self):
        return self.device + " " + self.name
    
class Vertex:
    def __init__(self, device, index) -> None:
        self.device = device
        self.index = index
        self.nextVertex = []
        
    def addNeighbor(self, nbr):
        self.nextVertex.append(nbr)
    
    def getNext(self):
        return self.nextVertex
    
    def getName(self):
        name = self.device + '.' + self.index
        return name
        
    def writeDpvToFile(self, outputfile):
        with open(outputfile,'a') as ofile:
            name1 = self.getName()
            for next in self.nextVertex:
                name2 = next.getName()
                edge = name1 + '-->' + name2 + ':[[]]\n'
                ofile.write(edge)
        
class Gragh:
    def __init__(self) -> None:
        self.vertexList = []
        self.numVertex = 0
        self.deviceList = {}
        
    
    # 获得预计的下一个Vertex的index
    def getFinalVertex(self,VertexDevice):
        if VertexDevice not in self.deviceList:
            self.deviceList[VertexDevice] = []
            self.deviceList[VertexDevice].append(True)
        else:
            self.deviceList[VertexDevice].append(True)
        
        return len(self.deviceList[VertexDevice])
        
    def addVertex(self,device,index):
        newVertex = Vertex(device,index)
        self.VertexList.append()
        