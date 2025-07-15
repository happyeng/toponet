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
 */

package org.sngroup.test.runner;

import org.sngroup.Configuration;
import org.sngroup.util.*;
import org.sngroup.verifier.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * EdgeConnectivityRunner - 高性能版本
 * 采用预加载、索引、批量处理等优化策略
 * 路径管理参考Configuration.java的方式
 */
public class EdgeConnectivityRunner extends TopoRunner {

    // 节点分类
    private Set<String> edgeDevices;
    private Set<String> aggDevices;
    private Set<String> coreDevices;

    // 连接关系
    private Map<String, Set<String>> edgeToAggCoreConnections;
    private Map<String, Set<String>> aggToEdgeConnections;
    private Map<String, Set<String>> coreToEdgeConnections;

    // 高性能数据结构
    private final Map<String, List<Rule>> preloadedRules = new ConcurrentHashMap<>();
    private final Map<String, Map<String, List<Rule>>> rulesByTargetPort = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<EdgeConnectivityResult> results = new ConcurrentLinkedQueue<>();

    // 性能参数
    private final int maxThreads = Runtime.getRuntime().availableProcessors() * 4;
    private final int devicesPerBatch = 200;
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(Math.min(50, maxThreads/2));
    private final ExecutorService computeExecutor = Executors.newFixedThreadPool(maxThreads);

    // ===== 文件路径管理，参考Configuration.java的方式 =====
    private static String networkName = "";
    private static String resultFileName = "edge_connectivity_results.txt";
    private static String resultFilePath = null;
    private static boolean isInitialized = false;
    private static boolean enableFileOutput = true;
    private final Object fileWriteLock = new Object();

    // 异步写入
    private final BlockingQueue<EdgeConnectivityResult> writeQueue = new LinkedBlockingQueue<>();
    private volatile boolean writingComplete = false;
    private BufferedWriter asyncWriter = null;

    public EdgeConnectivityRunner() {
        super();
        this.edgeDevices = new HashSet<>();
        this.aggDevices = new HashSet<>();
        this.coreDevices = new HashSet<>();
        this.edgeToAggCoreConnections = new HashMap<>();
        this.aggToEdgeConnections = new HashMap<>();
        this.coreToEdgeConnections = new HashMap<>();
    }

    // ===== 路径管理方法，参考Configuration.java的方式 =====

    /**
     * 从命令行参数初始化网络名称和路径，参考Configuration.readDirectory()
     * @param args 命令行参数
     */
    public static void initializeFromArgs(String[] args) {
        // 解析命令行参数获取网络名称
        if (args != null && args.length >= 2) {
            for (int i = 0; i < args.length - 1; i++) {
                if ("edge-connectivity".equals(args[i])) {
                    String networkParam = args[i + 1];
                    // 如果参数包含"/"，取第一部分作为网络名称
                    if (networkParam.contains("/")) {
                        networkParam = networkParam.substring(0, networkParam.indexOf("/"));
                    }
                    setNetworkName(networkParam);
                    break;
                }
            }
        }

        // 初始化文件写入并输出开始提示
        initializeFileWriting();
    }

    /**
     * 设置网络名称，参考Configuration.java的路径构建方式
     * @param networkName 网络名称
     */
    public static void setNetworkName(String networkName) {
        EdgeConnectivityRunner.networkName = networkName;
        updateResultFilePath();
    }

    /**
     * 更新结果文件路径，参考Configuration.getDeviceRuleFile()的路径构建方式
     */
    private static void updateResultFilePath() {
        if (networkName != null && !networkName.isEmpty()) {
            // 参考Configuration.java中：dirname = dir + networkName
            String dirname = "config/" + networkName;
            // 参考Configuration.java中：path + ((path.endsWith("/")?"":"/")+filename)
            resultFilePath = dirname + (dirname.endsWith("/") ? "" : "/") + resultFileName;
        } else {
            resultFilePath = resultFileName; // 默认路径
        }
    }

    /**
     * 初始化文件写入，只在程序开始时调用一次，输出开始提示
     */
    private static void initializeFileWriting() {
        if (!enableFileOutput || isInitialized) {
            return;
        }

        synchronized (EdgeConnectivityRunner.class) {
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
                System.out.println("Edge连接性验证结果存放路径: " + resultFile.getAbsolutePath());
                System.out.println("开始Edge连接性验证和结果写入...");

                isInitialized = true;

            } catch (Exception e) {
                System.err.println("初始化Edge连接性验证文件写入失败: " + e.getMessage());
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

        synchronized (EdgeConnectivityRunner.class) {
            try {
                // ===== 输出结束提示（只输出一次）=====
                if (resultFilePath != null) {
                    File resultFile = new File(resultFilePath);
                    System.out.println("Edge连接性验证结果存放路径: " + resultFile.getAbsolutePath());
                    System.out.println("Edge连接性验证结果写入完成。");
                }

                isInitialized = false;

            } catch (Exception e) {
                System.err.println("完成Edge连接性验证文件写入失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public Device getDevice(String name) {
        return null; // 不再需要Device对象
    }

    @Override
    public ThreadPool getThreadPool() {
        return null; // 使用自定义ExecutorService
    }

    @Override
    public void build() {
        System.out.println("==========================================");
        System.out.println("开始构建高性能Edge连接性验证器");
        System.out.println("网络名称: " + networkName);
        System.out.println("==========================================");

        long startTime = System.currentTimeMillis();

        // 1. 分析拓扑，识别节点类型
        analyzeTopologyAndClassifyNodes();

        // 2. 找出连接关系
        findConnectionRelationships();

        // 3. 预加载所有规则到内存
        preloadAllRules();

        // 4. 构建规则索引
        buildRuleIndexes();

        // 5. 启动异步写入线程
        startAsyncWriter();

        long endTime = System.currentTimeMillis();
        System.out.println("构建完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("预加载规则: " + preloadedRules.size() + " 个设备");
    }

    /**
     * 分析拓扑结构，识别节点类型
     */
    private void analyzeTopologyAndClassifyNodes() {
        for (String deviceName : network.devicePorts.keySet()) {
            if (isEdgeDevice(deviceName)) {
                edgeDevices.add(deviceName);
            } else if (isAggDevice(deviceName)) {
                aggDevices.add(deviceName);
            } else if (isCoreDevice(deviceName)) {
                coreDevices.add(deviceName);
            } else {
                aggDevices.add(deviceName); // 默认为agg
            }
        }

        System.out.println("节点分类: Edge(" + edgeDevices.size() + "), Agg(" +
                         aggDevices.size() + "), Core(" + coreDevices.size() + ")");
    }

    private boolean isEdgeDevice(String deviceName) {
        String lowerName = deviceName.toLowerCase();
        return lowerName.startsWith("edge") && lowerName.contains("i");
    }

    private boolean isAggDevice(String deviceName) {
        return deviceName.toLowerCase().startsWith("aggr");
    }

    private boolean isCoreDevice(String deviceName) {
        String lowerName = deviceName.toLowerCase();
        return lowerName.contains("core") || lowerName.contains("spine");
    }

    /**
     * 找出所有连接关系
     */
    private void findConnectionRelationships() {
        for (String edgeDevice : edgeDevices) {
            Set<String> connectedAggCores = new HashSet<>();
            Map<String, Set<DevicePort>> connections = network.devicePorts.get(edgeDevice);

            if (connections != null) {
                for (String connectedDevice : connections.keySet()) {
                    if (aggDevices.contains(connectedDevice)) {
                        connectedAggCores.add(connectedDevice);
                        aggToEdgeConnections.computeIfAbsent(connectedDevice, k -> new HashSet<>()).add(edgeDevice);
                    } else if (coreDevices.contains(connectedDevice)) {
                        connectedAggCores.add(connectedDevice);
                        coreToEdgeConnections.computeIfAbsent(connectedDevice, k -> new HashSet<>()).add(edgeDevice);
                    }
                }
            }

            if (!connectedAggCores.isEmpty()) {
                edgeToAggCoreConnections.put(edgeDevice, connectedAggCores);
            }
        }

        int totalConnections = edgeToAggCoreConnections.values().stream().mapToInt(Set::size).sum() +
                              aggToEdgeConnections.values().stream().mapToInt(Set::size).sum() +
                              coreToEdgeConnections.values().stream().mapToInt(Set::size).sum();
        System.out.println("连接关系: " + totalConnections + " 个连接");
    }

    /**
     * 预加载所有规则到内存（并行I/O）
     */
    private void preloadAllRules() {
        System.out.println("开始预加载规则...");
        long startTime = System.currentTimeMillis();

        Set<String> allDevices = new HashSet<>();
        allDevices.addAll(edgeDevices);
        allDevices.addAll(aggDevices);
        allDevices.addAll(coreDevices);

        // 并行读取所有规则文件
        CompletableFuture<Void>[] futures = allDevices.stream()
            .map(deviceName -> CompletableFuture.runAsync(() -> {
                loadDeviceRulesOptimized(deviceName);
            }, ioExecutor))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        long endTime = System.currentTimeMillis();
        System.out.println("规则预加载完成，耗时: " + (endTime - startTime) + "ms");
    }

    /**
     * 优化的规则加载，使用Configuration.getDeviceRuleFile()获取文件路径
     */
    private void loadDeviceRulesOptimized(String deviceName) {
        try {
            // 参考Configuration.java的方式获取规则文件路径
            String rulesFile = Configuration.getConfiguration().getDeviceRuleFile(deviceName);
            File file = new File(rulesFile);

            if (!file.exists()) {
                return;
            }

            List<Rule> rules = new ArrayList<>();

            // 使用BufferedReader提高读取性能
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
                    32768)) { // 32KB buffer

                String line;
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 4 &&
                        (tokens[0].equals("fw") || tokens[0].equals("ALL") ||
                         tokens[0].equals("ANY") || tokens[0].equals("any"))) {

                        try {
                            Collection<String> ports = new HashSet<>();
                            for (int i = 3; i < tokens.length; i++) {
                                ports.add(tokens[i]);
                            }

                            long ip = Long.parseLong(tokens[1]);
                            int prefix = Integer.parseInt(tokens[2]);
                            ForwardType ft = tokens[0].equals("ANY") ? ForwardType.ANY : ForwardType.ALL;

                            Rule rule = new Rule(ip, prefix, ports, ft);
                            rules.add(rule);
                        } catch (NumberFormatException e) {
                            // 跳过无效规则
                        }
                    }
                }
            }

            if (!rules.isEmpty()) {
                preloadedRules.put(deviceName, rules);
            }

        } catch (IOException e) {
            // 静默处理IO错误
        }
    }

    /**
     * 构建规则索引以加速查询
     */
    private void buildRuleIndexes() {
        System.out.println("构建规则索引...");

        for (Map.Entry<String, List<Rule>> entry : preloadedRules.entrySet()) {
            String deviceName = entry.getKey();
            List<Rule> rules = entry.getValue();

            Map<String, List<Rule>> portRules = new HashMap<>();

            for (Rule rule : rules) {
                for (String port : rule.forwardAction.ports) {
                    portRules.computeIfAbsent(port, k -> new ArrayList<>()).add(rule);
                }
            }

            rulesByTargetPort.put(deviceName, portRules);
        }

        System.out.println("规则索引构建完成");
    }

    @Override
    public void start() {
        System.out.println("开始高性能验证...");
        long startTime = System.currentTimeMillis();

        // 收集所有源设备
        List<String> allSourceDevices = new ArrayList<>();
        allSourceDevices.addAll(edgeDevices);
        allSourceDevices.addAll(aggDevices);
        allSourceDevices.addAll(coreDevices);

        System.out.println("总源设备数: " + allSourceDevices.size());

        // 分批并行处理
        List<List<String>> batches = createBatches(allSourceDevices, devicesPerBatch);
        System.out.println("分" + batches.size() + "批处理，每批" + devicesPerBatch + "个设备");

        // 并行处理所有批次
        AtomicInteger completedBatches = new AtomicInteger(0);
        CompletableFuture<Void>[] batchFutures = batches.stream()
            .map(batch -> CompletableFuture.runAsync(() -> {
                processBatchOptimized(batch);
                int completed = completedBatches.incrementAndGet();
                if (completed % 10 == 0) {
                    System.out.println("已完成批次: " + completed + "/" + batches.size());
                }
            }, computeExecutor))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(batchFutures).join();

        // 完成写入
        completeWriting();

        long endTime = System.currentTimeMillis();
        System.out.println("验证完成！总耗时: " + (endTime - startTime) + "ms");
        System.out.println("总验证结果数: " + results.size());

        // 显示验证结果汇总
        showVerificationSummary();

        // 调用完成方法，输出结束提示
        finalizeFileWriting();

        // 关闭线程池
        ioExecutor.shutdown();
        computeExecutor.shutdown();
    }

    /**
     * 创建批次
     */
    private List<List<String>> createBatches(List<String> devices, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < devices.size(); i += batchSize) {
            int end = Math.min(i + batchSize, devices.size());
            batches.add(devices.subList(i, end));
        }
        return batches;
    }

    /**
     * 优化的批次处理
     */
    private void processBatchOptimized(List<String> batchDevices) {
        for (String srcDevice : batchDevices) {
            Set<String> targetDevices = getTargetDevices(srcDevice);
            if (targetDevices == null || targetDevices.isEmpty()) {
                continue;
            }

            // 批量验证该源设备到所有目标设备的连接
            List<EdgeConnectivityResult> deviceResults = verifyDeviceConnections(srcDevice, targetDevices);

            // 异步写入结果
            for (EdgeConnectivityResult result : deviceResults) {
                writeQueue.offer(result);
                results.offer(result);
            }
        }
    }

    /**
     * 获取源设备的目标设备列表
     */
    private Set<String> getTargetDevices(String srcDevice) {
        if (edgeDevices.contains(srcDevice)) {
            return edgeToAggCoreConnections.get(srcDevice);
        } else if (aggDevices.contains(srcDevice)) {
            return aggToEdgeConnections.get(srcDevice);
        } else if (coreDevices.contains(srcDevice)) {
            return coreToEdgeConnections.get(srcDevice);
        }
        return null;
    }

    /**
     * 验证单个设备到多个目标的连接（批量优化）
     */
    private List<EdgeConnectivityResult> verifyDeviceConnections(String srcDevice, Set<String> targetDevices) {
        List<EdgeConnectivityResult> results = new ArrayList<>();
        List<Rule> srcRules = preloadedRules.get(srcDevice);

        if (srcRules == null || srcRules.isEmpty()) {
            return results;
        }

        // 为每个目标设备验证连接
        for (String dstDevice : targetDevices) {
            Set<String> reachableSegments = extractReachableSegmentsOptimized(srcDevice, dstDevice, srcRules);

            if (!reachableSegments.isEmpty()) {
                EdgeConnectivityResult result = new EdgeConnectivityResult();
                result.srcDevice = srcDevice;
                result.dstDevice = dstDevice;
                result.reachableSegments = reachableSegments;
                result.isConnected = true;
                result.verificationTime = System.currentTimeMillis();
                results.add(result);
            }
        }

        return results;
    }

    /**
     * 优化的网段提取算法
     */
    private Set<String> extractReachableSegmentsOptimized(String srcDevice, String dstDevice, List<Rule> srcRules) {
        Set<String> reachableSegments = new HashSet<>();

        // 找出连接到目标设备的端口
        Set<String> targetPorts = getPortsToTarget(srcDevice, dstDevice);
        if (targetPorts.isEmpty()) {
            return reachableSegments;
        }

        // 使用索引快速查找规则
        Map<String, List<Rule>> portRules = rulesByTargetPort.get(srcDevice);
        if (portRules != null) {
            for (String targetPort : targetPorts) {
                List<Rule> rules = portRules.get(targetPort);
                if (rules != null) {
                    for (Rule rule : rules) {
                        String segment = ipPrefixToSegment(rule.ip, rule.prefixLen);
                        reachableSegments.add(segment);
                    }
                }
            }
        }

        return reachableSegments;
    }

    /**
     * 获取源设备连接到目标设备的端口
     */
    private Set<String> getPortsToTarget(String srcDevice, String dstDevice) {
        Set<String> ports = new HashSet<>();
        Map<String, Set<DevicePort>> connections = network.devicePorts.get(srcDevice);

        if (connections != null) {
            Set<DevicePort> devicePorts = connections.get(dstDevice);
            if (devicePorts != null) {
                for (DevicePort dp : devicePorts) {
                    ports.add(dp.getPortName());
                }
            }
        }

        return ports;
    }

    /**
     * IP前缀转网段字符串
     */
    private String ipPrefixToSegment(long ip, int prefixLen) {
        return String.format("%d.%d.%d.%d/%d",
                           (ip >> 24) & 0xFF, (ip >> 16) & 0xFF,
                           (ip >> 8) & 0xFF, ip & 0xFF, prefixLen);
    }

    /**
     * 启动异步写入线程
     */
    private void startAsyncWriter() {
        Thread writerThread = new Thread(() -> {
            try {
                // 确保路径已更新
                updateResultFilePath();

                asyncWriter = new BufferedWriter(new FileWriter(resultFilePath), 65536); // 64KB buffer
                int writeCount = 0;

                // 写入文件头部信息
                asyncWriter.write("=== Edge连接性验证结果 ===\n");
                asyncWriter.write("网络名称: " + networkName + "\n");
                asyncWriter.write("生成时间: " + new Date() + "\n");
                asyncWriter.write("=============================\n\n");

                while (!writingComplete || !writeQueue.isEmpty()) {
                    EdgeConnectivityResult result = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (result != null) {
                        String devicePair = result.srcDevice + "-" + result.dstDevice;
                        String segmentList = String.join(", ", result.reachableSegments);
                        asyncWriter.write(devicePair + ":" + segmentList + "\n");
                        writeCount++;

                        // 定期刷新
                        if (writeCount % 1000 == 0) {
                            asyncWriter.flush();
                        }
                    }
                }

                // 写入文件尾部信息
                asyncWriter.write("\n=============================\n");
                asyncWriter.write("验证完成时间: " + new Date() + "\n");
                asyncWriter.write("总连接数: " + writeCount + "\n");

                asyncWriter.close();
                asyncWriter = null;

            } catch (Exception e) {
                System.err.println("异步写入文件失败: " + e.getMessage());
                e.printStackTrace();
            }
        });

        writerThread.setDaemon(true);
        writerThread.start();
    }

    /**
     * 完成写入过程
     */
    private void completeWriting() {
        writingComplete = true;
        try {
            Thread.sleep(1000); // 等待写入线程完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        completeWriting();
        if (!ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
        if (!computeExecutor.isShutdown()) {
            computeExecutor.shutdown();
        }
    }

    @Override
    public void awaitFinished() {
        // 已在start()方法中等待完成
    }

    @Override
    public void sendCount(Context ctx, DevicePort sendPort, BDDEngine bddEngine) {
        // 高性能版本不需要此方法
    }

    @Override
    public long getInitTime() {
        return 0;
    }

    /**
     * 显示验证结果汇总
     */
    private void showVerificationSummary() {
        System.out.println("\n========== Edge连接性验证结果汇总 ==========");
        System.out.println("总验证连接数: " + results.size());
        System.out.println("Edge设备数: " + edgeDevices.size());
        System.out.println("Agg设备数: " + aggDevices.size());
        System.out.println("Core设备数: " + coreDevices.size());
        System.out.println("===========================================\n");
    }

    /**
     * 验证结果类
     */
    private static class EdgeConnectivityResult {
        String srcDevice;
        String dstDevice;
        Set<String> reachableSegments;
        boolean isConnected;
        long verificationTime;
    }
}