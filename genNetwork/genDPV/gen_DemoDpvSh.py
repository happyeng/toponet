from RunnerDemo import *
from Utility_getNode import * 

if __name__ == "__main__":
    
    # print("Fattree")
    
    # src = "edgei0i0"
    # dst = "edgei2i1"
    src = "LFRZ_A3M1_203_01_01_10_S2_0_7"
    dst = "LFRZ_A18_603_01_01_43_S2_0_0"
    match = "exist >= 1"
    
    topo = "fattree6"
    topologyPath = "../../config/" + topo + "/topology"
    # dpvnetfile_path = "../../config/" + topo + "/DPVNet7.puml"
    
    runner = RunnerDemo(topologyPath)
    
    # runner.out_one_dpvnet(0, src, dst ,match, True, True)
    
    # node_pair = [("LFRZ_A3M1_203_01_01_10_S2_0_7", "LFRZ_A3M1_203_01_01_20_S2_0_5"),
    #              ("LFRZ_A3M1_203_01_01_20_S2_0_5","LFRZ_A3M1_203_01_01_10_S2_0_7"),
    #              ("LFRZ_A18_603_01_01_43_S2_0_0","LFRZ_A3M1_203_01_01_10_S2_0_7"),
    #              ("LFRZ_A3M1_203_01_01_10_S2_0_7","LFRZ_A18_603_01_01_43_S2_0_0"),
    #              ]
    # runner.out_multi_pair_dpvnet("../../config/" + topo + "/DPVNet3.puml",node_pair)
    
    # n_num = 2
    # dpvnetfile_path = "../../config/" + topo + "/DPVNet1.puml"
    # runner.out_all_pair_dpvnet(dpvnetfile_path, n_num, n_num)
    
    # dpvDir = "../../config/" + topo + "/oneDpvnet"
    # runner.out_multi_onepair_dpvnet(dpvDir, n_num, n_num)
    
    # n_num = 10
    # dpvnetfile_path = "../../config/" + topo + "/DPVNettt.puml"
    # runner.out_multi_sameDst_dpvnet(dpvnetfile_path, n_num, n_num, 2)
    
    
    n_num = 10
    dpvDir = "../../config/" + topo + "/oneDPVNet10"
    runner.out_multi_sameDst_dpvnet_toDir(dpvDir, n_num, n_num, n_num)
    dpvnetfile_path = "../../config/" + topo + "/DPVNet10.puml"
    runner.out_multi_sameDst_dpvnet(dpvnetfile_path, n_num, n_num, n_num)
    