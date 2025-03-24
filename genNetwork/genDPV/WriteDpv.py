
def write_start(output):
    with open(output, mode="w", encoding="utf-8") as f:
        f.write("@startuml\n")

_NAME = "DPVNet"
def write_head(output, index, src, dst, match,srcNmae):
    with open(output, mode="a", encoding="utf-8") as f:
        f.write("state %s%d {\n" % (_NAME, index))
        f.write("'packet_space:%s\n" % dst)
        f.write("'ingress:['%s']\n" % src)
        f.write("'match: %s\n" % match)
        f.write("'path: %s.*%s\n" % (src, dst))
        f.write("[*]-->%s:[[]]\n" % srcNmae)
        
def write_head(output, index, src, dst, match,srcNmae):
    with open(output, mode="a", encoding="utf-8") as f:
        f.write("state %s%d {\n" % (_NAME, index))
        f.write("'packet_space:%s\n" % dst)
        f.write("'ingress:['%s']\n" % src)
        f.write("'match: %s\n" % match)
        f.write("'path: %s.*%s\n" % (src, dst))
        f.write("[*]-->%s:[[]]\n" % srcNmae)

def write_annotation(output, index, srcList, dst, match):
    with open(output, mode="a", encoding="utf-8") as f:
        f.write("state %s%d {\n" % (_NAME, index))
        f.write("'packet_space:%s\n" % dst)
        f.write("'ingress:['%s']\n" % srcList)
        f.write("'match: %s\n" % match)
        f.write("'path: %s.*%s\n" % (srcList, dst))
        
def write_path_start(output, srcNmae):
    with open(output, mode="a", encoding="utf-8") as f:
        f.write("[*]-->%s:[[]]\n" % srcNmae)

def write_path_fat(output, path): # path 中存储为 Node 数据结构
    with open(output, mode="a", encoding="utf-8") as f:
        for i in range(len(path)-1):
            name1 = path[i].getName()
            name2 = path[i+1].getName()
            edge = name1 + '-->' + name2 + ':[[]]\n'
            
            f.write(edge)

def write_path(output, path): # path 中存储为 Node 数据结构
    with open(output, mode="a", encoding="utf-8") as f:
        for i in range(len(path)-1):
            name1 = path[i].getName()
            name2 = path[i+1].getName()
            edge = name1 + '-->' + name2 + ':[[]]\n'
            
            f.seek(0)  # 移动文件指针到文件开头
            existing_paths = f.readlines()
            if edge not in existing_paths:
                f.write(edge)

def write_last(output, dstName):
    with open(output, mode="a", encoding="utf-8") as f:
        f.write("%s-->[*]:[[]]\n" % dstName)
        f.write("}\n")
    
def write_end(output):
    with open(output, mode="a", encoding="utf-8") as f:
        f.write("@enduml\n")