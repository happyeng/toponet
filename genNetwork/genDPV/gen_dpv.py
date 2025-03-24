from Runner import *

if __name__ == "__main__":
    
    topo_file = "/home/dfy/project/byte/DPV-byte/config/demo/topology"
    runner = Runner(topo_file)
    runner.out_one_dpvnet(0,"S","D","exist >= 1")
    
    print(runner.getNetwork())