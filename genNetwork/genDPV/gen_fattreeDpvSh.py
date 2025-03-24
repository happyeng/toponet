from RunnerFattree import *

if __name__ == "__main__":
    
    print("Fattree")
    kvalue = 20
    
    src = "edgei0i0"
    dst = "edgei2i1"
    match = "exist >= 1"
    
    runner = RunnerFattree(kvalue)
    runner.out_one_dpvnet(0, src, dst ,match, True, True)
    # runner.out_all_pair_dpvnet("../../config/fattree" + str(kvalue) + "/DPVNet.puml")
    
    # def out_one_fattree_dpvnet(int k):
    
    
    # outputPath = "../../config/fattree" + str(k) + "/DPVNet1.puml"
    # stateIndex = 0
    
    # write_start(outputPath)
    # write_head(outputPath, stateIndex, src, dst, match, dpv.getSrc().getName())
    
    
    # dfs(src,dst,src,[],[])
    
    # write_last(outputPath, dpv.getDst().getName())
    # write_end(outputPath)
    
    # print("Write to " + outputPath)