/*
 * This program is free software: you can redistribute it and/or modify it under the terms of
 *  the GNU General Public License as published by the Free Software Foundation, either
 *   version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *   PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 *  program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Authors: Chenyang Huang (Xiamen University) <xmuhcy@stu.xmu.edu.cn>
 *          Qiao Xiang     (Xiamen University) <xiangq27@gmail.com>
 *          Ridi Wen       (Xiamen University) <23020211153973@stu.xmu.edu.cn>
 *          Yuxin Wang     (Xiamen University) <yuxxinwang@gmail.com>
 */

 package org.sngroup.test.runner;

// import com.fasterxml.jackson.core.JsonProcessingException;
 import jdd.bdd.BDD;
// import jdk.management.jfr.ConfigurationInfo;
// import org.apache.commons.lang3.SerializationUtils;
 import org.sngroup.Configuration;
 import org.sngroup.util.*;
// import org.sngroup.util.CopyHelper.FSTDeepCopy;
 //import org.sngroup.util.CopyHelper.KryoDeepCopy;
// import org.sngroup.util.CopyHelper.ReflectDeepCopy;
 import org.sngroup.verifier.*;
// import java.lang.management.ManagementFactory;
 
 
 import java.io.*;
 import java.lang.management.ThreadMXBean;
 import java.net.UnknownHostException;
 import java.util.*;
 import java.util.concurrent.ExecutorService;
 import java.util.concurrent.Executors;
 import java.util.concurrent.LinkedBlockingDeque;
 import java.util.concurrent.atomic.AtomicInteger;
// import com.sun.jna.Library;
// import com.sun.jna.Native;
// import com.sun.jna.Pointer;
 
 
 public class TopoRunner extends Runner {
 
     private static final int THREAD_POOL_READ_SIZE = 1; // 设置线程池大小
     public ThreadPool threadPool;
     // private final Serialization srl;
     public static Map<String, Device> devices;
 
     public int ruleCnt = 0;
 
     public static boolean isIpv6 = false;
     public static boolean isIpv4withS = true;
 
     private int poolSize =  20;
 
     private static Map<String, TopoNet> topoNetMap;
 
     private static Map<Integer, HashSet<TopoNet>> topoNetGroupMap = new HashMap<>();
     Set<Integer> dvNetSet;
 
     public static DVNet srcNet;
 
     public static BDDEngine srcBdd;
 
     public TopoRunner(){
         super();
         devices = new HashMap<>();
         dvNetSet = new HashSet<>();
         topoNetMap = new HashMap<>();
     }
 
     public ThreadPool getThreadPool(){
         return threadPool;
     }
     public Device getDevice(String name){
         return devices.get(name);
     }
 
     public void build(){
         // IPV6 OR IPV4
         if(isIpv6) BDDEngine.ipBits = 128;
         else BDDEngine.ipBits = 32;
         srcBdd = new BDDEngine();
 
         System.out.println("Start Build in Runner!!!");
         srcNet = new DVNet(-1, srcBdd);
         threadPool = ThreadPool.FixedThreadPool(Configuration.getConfiguration().getThreadPoolSize());
         devices.clear();
         // 读取所有的device
         for(String deviceName : network.devicePorts.keySet()){
             Device d = new Device(deviceName, network, this, threadPool);
             if(network.edgeDevices.contains(deviceName)){
                 TopoNet.edgeDevices.add(d);
             }
             devices.put(deviceName, d);
         }
         // device读取规则
         if(isIpv6 || isIpv4withS) readRuleByDeviceIPV6();  
         else readRuleByDevice();
         // srcBDD转化规则
         srcBddTransformAllRules();
         // 生成topoNet
         genTopoNet();
         System.out.println("结点总数量" + devices.size());
         System.out.println("S0结点数量" + network.edgeDevices.size());
         System.out.println("表项总数量" + ruleCnt);
 
         System.out.println("End Build in Runner!!");
     }
 
     public void start() {
         AtomicInteger netCNt = new AtomicInteger(1);
         LinkedBlockingDeque<BDDEngine> sharedQueueBDD = new LinkedBlockingDeque<>();
         for(Map.Entry<String, TopoNet>entry : topoNetMap.entrySet()){
             threadPool.execute(() -> {
                 TopoNet topoNet = entry.getValue();
                 boolean reused = topoNet.getAndSetBddEngine(sharedQueueBDD);
 //                topoNodeInit_ser(topoNet);
                 topoGenNode(topoNet);
                 topoNetDeepCopyBdd(topoNet, reused);
                 topoNet.nodeCalIndegree();
                 topoNet.startCount(sharedQueueBDD);
             });
         }
     }
 
     private void genTopoNet( ) {
         topoNetMap = new HashMap<>();
         int topoCnt = -1;
         // 根据edgeDevices初始化topoNet对象并设置device
         TopoNet.network = this.network;
         for(Device dstDevice : TopoNet.edgeDevices){
             if(!network.dstDevices.contains(dstDevice.name)) continue;
             TopoNet topoNet = new TopoNet(dstDevice, topoCnt);
             topoNet.setInvariant(dstDevice.name, "exist >= 1", "*");
             topoNetMap.put(dstDevice.name, topoNet);
             topoCnt--;
         }
         TopoNet.transformDevicePorts(network.devicePorts);
         TopoNet.setNextTable();
     }
 
     private void readRuleByDevice(){
         // 先从文件中读取规则, 并插入规则
         for (Map.Entry<String, Device> entry : devices.entrySet()) {
             threadPool.execute(() -> {
                 String name = entry.getKey();
                 Device device = entry.getValue();
                 device.readOnlyRulesFile(Configuration.getConfiguration().getDeviceRuleFile(name));
             });
         }
         // 读取网段
         Device.readOnlySpaceFile(Configuration.getConfiguration().getSpaceFile());
         threadPool.awaitAllTaskFinished();
     }
 
     private void readRuleByDeviceIPV6(){
         // 先从文件中读取规则, 并插入规则
         for (Map.Entry<String, Device> entry : devices.entrySet()) {
             threadPool.execute(() -> {
                 String name = entry.getKey();
                 Device device = entry.getValue();
                 if(isIpv4withS){
                    device.readOnlyRulesFileIPV4_S(Configuration.getConfiguration().getDeviceRuleFile(name));
                 }else{
                    device.readOnlyRulesFileIPV6(Configuration.getConfiguration().getDeviceRuleFile(name));
                 }
                 
             });
         }
         // 读取网段
         Device.readOnlySpaceFileIPV6(Configuration.getConfiguration().getSpaceFile());
         threadPool.awaitAllTaskFinished();
     }
 
     public void srcBddTransformAllRules(){
        // transformRuleWithTrie();  // 目前只支持ipv4下的十进制读取，即该分支
        // transformRuleWithTrieIPV6(); // IPV6 unfinished
        transformRuleWithoutTrie(); // 仍然存在bug
     }
 
     public void transformRuleWithTrie(){
         long timePoint1 = System.currentTimeMillis();
         for(Device device : devices.values()){
             device.encodeDeviceRule(srcNet);
         }
         long timePoint2 = System.currentTimeMillis();
         System.out.println("规则转化所使用的时间" + (timePoint2 - timePoint1) + "ms");
         srcNet.srcDvNetParseAllSpace(Device.spaces);
         long timePoint3 = System.currentTimeMillis();
         System.out.println("BDD编码所使用的总时间" + (timePoint3 - timePoint1) + "ms");
     }

     public void transformRuleWithTrieIPV6(){
        long timePoint1 = System.currentTimeMillis();
        for(Device device : devices.values()){
            device.encodeDeviceRuleIPV6(srcNet);
        }
        long timePoint2 = System.currentTimeMillis();
        System.out.println("规则转化所使用的时间" + (timePoint2 - timePoint1) + "ms");
        srcNet.srcDvNetParseAllSpaceIPV6(Device.spacesIPV6);
        long timePoint3 = System.currentTimeMillis();
        System.out.println("BDD编码所使用的总时间" + (timePoint3 - timePoint1) + "ms");
    }

 
     public void transformRuleWithoutTrie(){
         long timePoint1 = System.currentTimeMillis();
         for(Device device : devices.values()){
             //TODO: 尚存在bug
 //            device.encodeRuleToLecFromScratchToFinish(srcNet);
             if(!(isIpv6 || isIpv4withS))device.encodeRuleToLecFromScratch(srcNet); // IPV4
             else {
                 try {
                     device.encodeRuleToLecFromScratchIPV6(srcNet); // IPV6
                 } catch (UnknownHostException e) {
                     throw new RuntimeException(e);
                 }
             }
             ruleCnt += device.rules.size();
         }
         long timePoint2 = System.currentTimeMillis();
         System.out.println("规则转化所使用的时间" + (timePoint2 - timePoint1) + "ms");
         if(!(isIpv6|| isIpv4withS))srcNet.srcDvNetParseAllSpace(Device.spaces);
         else srcNet.srcDvNetParseAllSpaceIPV6(Device.spacesIPV6);
         long timePoint3 = System.currentTimeMillis();
         System.out.println("BDD编码所使用的总时间" + (timePoint3 - timePoint1) + "ms");
     }
 
     private void topoGenNode(TopoNet topoNet){
         for(Device device : devices.values()){
 //            Node node = new Node(device, topoNet.topoCnt, topoNet.invariant, topoNet.runner);
             Node node = new Node(device, topoNet);
             topoNet.nodesTable.put(device.name, node);
             if(TopoNet.edgeDevices.contains(device)){
                 if(device == topoNet.dstDevice) { // 终结点
                     topoNet.setDstNode(node);
                     node.isDestination = true;
                 }
                 else{ // 边缘结点
                     topoNet.srcNodes.add(node);
                 }
             }
         }
     }
 
     public void topoNetDeepCopyBdd(TopoNet topoNet, boolean reused){
         String dstDevice = topoNet.dstDevice.name;
         int s = DVNet.devicePacketSpace.get(dstDevice);
         if(!reused) {
             try {
                 topoNet.copyBdd(srcBdd, "Reflect");
             } catch (Exception e) {
                 throw new RuntimeException(e);
             }
         }
         else topoNet.setNodeBdd();
         topoNet.deviceLecs = Device.globalLecs;
         topoNet.setPacketSpace(s);
     }
 
     @Override
     public void awaitFinished(){
         threadPool.awaitAllTaskFinished(100);
     }
 
     @Override
     public void sendCount(Context ctx, DevicePort sendPort, BDDEngine bddEngine) {
 
     }
 
     public long getInitTime(){
         return 0;
     }
 
     @Override
     public void close(){
         devices.values().forEach(Device::close);
         threadPool.shutdownNow();
     }
 
 }
 