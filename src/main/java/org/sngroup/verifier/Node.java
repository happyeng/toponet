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

package org.sngroup.verifier;

import org.sngroup.Configuration;
import org.sngroup.test.runner.Runner;
import org.sngroup.test.runner.TopoRunner;
import org.sngroup.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileWriter;
import java.io.File;
import java.nio.file.*;

public class Node {

    public static AtomicInteger numDpvnet = new AtomicInteger(1);

    public static Map<DevicePort, DevicePort> topology = new HashMap<>();

    public static Map<String, Device> devices = new Hashtable<>();

    public Device device;
    public int index;
    public static Map<String, HashSet<NodePointer>> nextTable = new HashMap<>();

    String deviceName;

    public TopoNet topoNet;

    public TSBDD bdd;

    public boolean hasResult;

    public CibMessage lastResult;

    Invariant invariant;

    protected Set<CibTuple> todoList;

    protected Vector<CibTuple> locCib;
    protected Map<String, List<CibTuple>> portToCib;

    public boolean isDestination = false;

    // ===== 文件写入功能相关变量，参考Configuration.java的方式 =====
    private static String resultFileName = "reachable_networks.txt";
    private static String networkName = "";
    private static String resultFilePath = null;
    private static final Object fileWriteLock = new Object();
    private static final Object outputLock = new Object();
    private static boolean isInitialized = false;
    private static boolean enableFileOutput = true;
    private static BufferedWriter fileWriter = null;

    // ===== 公共方法：参考Configuration.java的readDirectory方式 =====

    /**
     * 设置网络名称，参考Configuration.readDirectory()的方式构建路径
     * @param networkName 网络名称，如"divided_compressed_network"
     */
    public static void setNetworkName(String networkName) {
        Node.networkName = networkName;
        // 参考Configuration.java中的路径构建方式：dir + networkName + "/" + filename
        updateResultFilePath();
    }

    /**
     * 更新结果文件路径，参考Configuration.getDeviceRuleFile()的路径构建方式
     */
    private static void updateResultFilePath() {
        if (networkName != null && !networkName.isEmpty()) {
            // 参考Configuration.java中：dirname = dir + networkName
            String dirname = "config/" + networkName;
            // 参考Configuration.java中：path + ((path.endsWith("/")?"":"/")+device)
            resultFilePath = dirname + (dirname.endsWith("/") ? "" : "/") + resultFileName;
        } else {
            resultFilePath = resultFileName; // 默认路径
        }
    }

    /**
     * 从命令行参数中获取网络名称，参考Configuration.readDirectory()的参数处理方式
     * @param args 命令行参数
     */
    public static void initializeFromArgs(String[] args) {
        // 解析命令行参数，获取网络名称
        if (args != null && args.length >= 2) {
            for (int i = 0; i < args.length - 1; i++) {
                if ("cbs".equals(args[i]) || "edge-connectivity".equals(args[i])) {
                    String fullParam = args[i + 1];
                    // 如果参数包含"/"，取第一部分作为网络名称
                    String extractedNetworkName = fullParam;
                    if (fullParam.contains("/")) {
                        extractedNetworkName = fullParam.substring(0, fullParam.indexOf("/"));
                    }
                    setNetworkName(extractedNetworkName);
                    break;
                }
            }
        }

        // 初始化文件写入并输出开始提示（只执行一次）
        initializeFileWriting();
    }

    /**
     * 初始化文件写入，只在程序开始时调用一次，输出开始提示
     */
    private static void initializeFileWriting() {
        if (!enableFileOutput || isInitialized) {
            return;
        }

        synchronized (fileWriteLock) {
            if (isInitialized) return; // 双重检查

            try {
                // 确保路径是最新的
                updateResultFilePath();

                // 创建目录（如果不存在），参考Configuration.java的目录检查方式
                File resultFile = new File(resultFilePath);
                File parentDir = resultFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // ===== 输出开始提示（只输出一次）=====
                System.out.println("验证结果存放路径: " + resultFile.getAbsolutePath());
                System.out.println("开始写入验证结果...");

                // 初始化文件写入器
                fileWriter = new BufferedWriter(new FileWriter(resultFilePath, false));

                // 写入文件头部信息
                fileWriter.write("=== 分布式数据平面验证结果 ===\n");
                fileWriter.write("网络名称: " + networkName + "\n");
                fileWriter.write("生成时间: " + new Date() + "\n");
                fileWriter.write("==============================\n\n");
                fileWriter.flush();

                isInitialized = true;

            } catch (IOException e) {
                System.err.println("初始化结果文件写入失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 完成文件写入，只在程序结束时调用一次，输出结束提示
     */
    public static void finalizeFileWriting() {
        if (!enableFileOutput || !isInitialized) {
            return;
        }

        synchronized (fileWriteLock) {
            try {
                if (fileWriter != null) {
                    // 写入文件尾部信息
                    fileWriter.write("\n==============================\n");
                    fileWriter.write("验证完成时间: " + new Date() + "\n");
                    fileWriter.write("总共验证的DPVnet数量: " + (numDpvnet.get() - 1) + "\n");
                    fileWriter.close();
                    fileWriter = null;
                }

                // ===== 输出结束提示（只输出一次）=====
                if (resultFilePath != null) {
                    File resultFile = new File(resultFilePath);
                    System.out.println("验证结果存放路径: " + resultFile.getAbsolutePath());
                    System.out.println("写入完成。");
                }

                isInitialized = false;

            } catch (IOException e) {
                System.err.println("完成结果文件写入失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 写入验证结果到文件
     */
    private void writeReachabilityToFile(String srcDevice, String dstDevice, String networks, boolean isReachable) {
        if (!enableFileOutput || !isInitialized) {
            return;
        }

        synchronized (fileWriteLock) {
            try {
                if (fileWriter != null) {
                    String content = String.format("%s-%s:%s%n", srcDevice, dstDevice, networks);
                    fileWriter.write(content);
                    fileWriter.flush();
                }
            } catch (IOException e) {
                System.err.println("写入文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 将Packet Space解码为网段格式
     */
    private String decodePacketSpaceToNetworks(int packetSpace) {
        try {
            if (topoNet.getBddEngine() == null || packetSpace == 0) {
                return "无效的Packet Space";
            }

            String rawOutput = topoNet.getBddEngine().printSet(packetSpace);
            if (rawOutput == null || rawOutput.isEmpty()) {
                return "空的Packet Space";
            }

            String[] ips = rawOutput.split(";");
            Set<String> networks = new LinkedHashSet<>();

            for (String ip : ips) {
                if (ip.trim().isEmpty()) continue;

                String cleanIp = ip.trim();
                if (cleanIp.contains("/")) {
                    networks.add(cleanIp);
                } else {
                    if (cleanIp.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                        networks.add(cleanIp);
                    }
                }
            }

            return String.join(", ", networks);

        } catch (Exception e) {
            return "解码失败: " + e.getMessage();
        }
    }

    // ===== 以下是原有代码，保持不变 =====

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Node(Device device, int index, Invariant invariant, Runner runner) {
        this.device = device;
        this.index = index;
        this.invariant = invariant;
        hasResult = false;
        todoList = new HashSet<>();
        locCib = new Vector<>();
        portToCib = new Hashtable<>();
        lastResult = null;
    }

    public Node(Device device, TopoNet topoNet) {
        this.device = device;
        this.topoNet = topoNet;
        this.invariant = topoNet.invariant;
        this.index = topoNet.topoCnt;
        this.deviceName = device.name;
        hasResult = false;
        todoList = new HashSet<>();
        locCib = new Vector<>();
        portToCib = new Hashtable<>();
        lastResult = null;
    }

    int getPacketSpace() {
        return topoNet.packetSpace;
    }

    public void setBdd(BDDEngine bddEngine) {
        this.bdd = bddEngine.getBDD();
    }

    public void setTopoNet(TopoNet topoNet) {
        this.topoNet = topoNet;
    }

    public void topoNetStart() {
        initializeCibByTopo();
    }

    public boolean checkIsSrcNode() {
        TopoNet topoNet = this.topoNet;
        return topoNet.srcNodes.contains(this);
    }

    public boolean updateLocCibByTopo(String from, Collection<Announcement> announcements) {
        boolean newResult = false;
        Queue<CibTuple> queue = new LinkedList<>(portToCib.get(from));
        if(queue.size() == 0) return true;
        while (!queue.isEmpty()) {
            CibTuple cibTuple = queue.poll();

            for (Announcement announcement : announcements) {
                int intersection = bdd.ref(bdd.and(announcement.predicate, cibTuple.predicate));
                if (intersection != cibTuple.predicate) {
                    CibTuple newCibTuple = cibTuple.keepAndSplit(intersection, bdd);
                    addCib(newCibTuple);
                    if (!hasResult && todoList.contains(cibTuple))
                        todoList.add(newCibTuple);
                    queue.add(newCibTuple);
                    return false;
                }
                newResult |= cibTuple.set(from, new Count(announcement.count));
                if (cibTuple.isDefinite()) {
                    todoList.remove(cibTuple);
                    break;
                }
            }
        }
        return newResult;
    }

    protected void addCib(CibTuple cib) {
        locCib.add(cib);
        updateActionPortTable(cib);
    }

    private void updateActionPortTable(CibTuple cib) {
        for (String port : cib.action.ports) {
            portToCib.putIfAbsent(port, new Vector<>());
            portToCib.get(port).add(cib);
        }
    }

    public void initializeCibByTopo() {
        for (NodePointer np : Node.nextTable.get(deviceName)) {
            portToCib.put(np.name, new Vector<>());
        }
        if (isDestination) {
            CibTuple _cibTuple = new CibTuple(getPacketSpace(), ForwardAction.getNullAction(), 0);
            _cibTuple.count.set(1);
            addCib(_cibTuple);
            return;
        }
        int cnt = 0;
        for (Lec lec : topoNet.getDeviceLecs(device.name)) {
            if (!isDestination && lec.forwardAction.ports.size() == 1) {
                int intersection = bdd.and(lec.predicate, getPacketSpace());
                if (intersection != 0) {
                    cnt += 1;
                    CibTuple cibTuple = new CibTuple(intersection, lec.forwardAction, 1);
                    addCib(cibTuple);
                    todoList.add(cibTuple);
                }
            }
        }
    }

    public Map<Count, Integer> getCibOut() {
        Map<Count, Integer> cibOut = new HashMap<>();
        for (CibTuple cibTuple : locCib) {
            if (cibTuple.predicate == 0)
                continue;
            if (cibOut.containsKey(cibTuple.count)) {
                int pre = cibOut.get(cibTuple.count);
                pre = bdd.orTo(pre, cibTuple.predicate);
                cibOut.put(cibTuple.count, pre);
            } else {
                cibOut.put(cibTuple.count, cibTuple.predicate);
            }
        }
        return cibOut;
    }

    public void bfsByIteration(Context c) {
        Announcement a = new Announcement(0, getPacketSpace(), Utility.getOneNumVector(1));
        Vector<Announcement> al = new Vector<>();
        al.add(a);
        CibMessage cibOut = new CibMessage(al, new ArrayList<>(), index);
        c.setCib(cibOut);
        c.setDeviceName(deviceName);
        Set<String> visited = new HashSet<>();
        Queue<Context> queue = new LinkedList<>();
        queue.add(c);
        int bfsCnt = 0;
        int ctxCnt = 0;
        int checkCnt = 0;
        System.out.println("终结点开始验证: " + c.getDeviceName());

        while (!queue.isEmpty()) {
            bfsCnt++;
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Context currentCtx = queue.poll();
                String curDeviceName = currentCtx.getDeviceName();
                visited.add(curDeviceName);
                HashSet<DevicePort> ps = TopoNet.devicePortsTopo.get(curDeviceName);

                int satisfiedCount = 0;

                if (ps != null) {
                    for (DevicePort p : ps) {
                        if (p.portName.equals("temp")) {
                            continue;
                        }
                        DevicePort dst = topology.get(p);
                        if (dst != null) {
                            String dstDeviceName = dst.deviceName;
                            checkCnt++;

                            if (!visited.contains(dstDeviceName)) {
                                Node dstNode = this.topoNet.getDstNodeByName(dst.deviceName);
                                Context ctx = new Context();
                                ctx.setCib(currentCtx.getCib());
                                int topoId = currentCtx.topoId;
                                ctx.setTopoId(topoId);
                                NodePointer np = new NodePointer(dst.getPortName(), topoId);

                                if (dstNode.countCheckByTopo(np, currentCtx)) {
                                    ctxCnt++;
                                    satisfiedCount++;
                                    List<Announcement> announcements = new LinkedList<>();
                                    Map<Count, Integer> nextCibOut = dstNode.getCibOut();
                                    for (Map.Entry<Count, Integer> entry : nextCibOut.entrySet())
                                        announcements.add(new Announcement(0, entry.getValue(), entry.getKey().count));
                                    CibMessage cibMessage = new CibMessage(announcements, new LinkedList<>(), index);
                                    ctx.setCib(cibMessage);
                                    ctx.setDeviceName(dstNode.deviceName);
                                    queue.add(ctx);
                                    visited.add(dst.deviceName);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("BFS结束，总遍历次数: " + bfsCnt + ", 满足条件的节点数: " + ctxCnt + ", 总检查次数: " + checkCnt);
    }

    protected boolean countCheckByTopo(NodePointer from, Context ctx) {
        CibMessage message = ctx.getCib();
        if (message != null) {
            if (locCib.size() == 0) {
                return false;
            }
            if (!updateLocCibByTopo(from.name, message.announcements)) {
                return false;
            }
            if (checkIsSrcNode()) {
                return false;
            }
            if (!hasResult && todoList.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void sendFirstResultByTopo(Context ctx, Set<String> visited) {
        List<Announcement> announcements = new LinkedList<>();
        Map<Count, Integer> cibOut = getCibOut();
        for (Map.Entry<Count, Integer> entry : cibOut.entrySet())
            announcements.add(new Announcement(0, entry.getValue(), entry.getKey().count));
        CibMessage cibMessage = new CibMessage(announcements, new LinkedList<>(), index);
        ctx.setCib(cibMessage);
        sendCountByTopo(ctx, visited);
        hasResult = true;
    }

    protected void countByTopo(NodePointer from, Context ctx, Set<String> visited) {
        CibMessage message = ctx.getCib();
        if (message != null) {
            if (!updateLocCibByTopo(from.name, message.announcements)) {
                return;
            }
            if (checkIsSrcNode()) {
                return;
            }
            if (!hasResult && todoList.isEmpty()) {
                sendFirstResultByTopo(ctx, visited);
            }
        }
    }

    public void sendCountByTopo(Context ctx, Set<String> visited) {
        lastResult = ctx.getCib();
        visited.add(deviceName);
        Collection<DevicePort> ps = TopoNet.devicePortsTopo.get(deviceName);
        if (ps != null) {
            for (DevicePort p : ps) {
                if (p.portName.equals("temp")) {
                    return;
                }
                transferByTopo(ctx, p, visited);
            }
            visited.remove(deviceName);
        } else
            System.out.println("No Forwarding Port!");
    }

    public void transferByTopo(Context oldCtx, DevicePort sendPort, Set<String> visited) {
        DevicePort dst = topology.get(sendPort);
        Node dstNode = this.topoNet.getDstNodeByName(dst.deviceName);
        if (!visited.contains(dst.deviceName)) {
            Context ctx = new Context();
            ctx.setCib(oldCtx.getCib());
            int topoId = oldCtx.topoId;
            ctx.setTopoId(topoId);
            NodePointer np = new NodePointer(dst.getPortName(), topoId);
            dstNode.countByTopo(np, ctx, visited);
        }
    }

    public void close() {
        // 清理逻辑
    }

    @Override
    public String toString() {
        return "";
    }

    /**
     * 显示验证结果，基于网段可达性判定并写入文件
     */
    public void showResult() {
        if (Configuration.getConfiguration().isShowResult()) {
            synchronized (outputLock) {
                Map<Count, Integer> cibOut = getCibOut();

                String srcDeviceName = this.deviceName;
                String dstDeviceName = topoNet.dstDevice != null ? topoNet.dstDevice.name : "Unknown";

                boolean success = false;
                String networks = "";

                try {
                    networks = decodePacketSpaceToNetworks(topoNet.packetSpace);

                    if (networks != null && !networks.isEmpty() &&
                        !networks.equals("无效的Packet Space") &&
                        !networks.equals("空的Packet Space") &&
                        !networks.startsWith("解码失败")) {

                        String[] networkArray = networks.split(",");
                        for (String network : networkArray) {
                            String trimmed = network.trim();
                            if (trimmed.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*") ||
                                trimmed.contains("/")) {
                                success = true;
                                break;
                            }
                        }
                    }

                    if (!success && cibOut != null && !cibOut.isEmpty()) {
                        for (Map.Entry<Count, Integer> entry : cibOut.entrySet()) {
                            if (entry.getValue() != null && entry.getValue() != 0) {
                                success = true;
                                break;
                            }
                        }
                    }

                } catch (Exception e) {
                    success = false;
                    networks = "解码异常: " + e.getMessage();
                }

                // 控制台输出
                System.out.println("源节点 (Source): " + srcDeviceName + " -> 目标节点 (Destination): " + dstDeviceName);
                System.out.println("invariants: (" + invariant.getMatch() + ", " + invariant.getPath()
                        + ", packet space:" + topoNet.packetSpace + ") , result: " + success);

                // 文件写入
                if (success) {
                    writeReachabilityToFile(srcDeviceName, dstDeviceName, networks, success);
                } else {
                    writeReachabilityToFile(srcDeviceName, dstDeviceName, "NULL", success);
                }

                System.out.println("Num of DPVnets been verified: " + numDpvnet.getAndIncrement());
                System.out.println();
            }
        }
    }
}