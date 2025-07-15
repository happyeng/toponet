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

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.sngroup.Configuration;
import org.sngroup.test.evaluator.Evaluator;
import org.sngroup.test.evaluator.BurstEvaluator;
//import org.sngroup.test.evaluator.IncrementalEvaluator;
import org.sngroup.test.runner.*;
import org.sngroup.verifier.Node;

import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {
    public static void main(String[] args) {
        Logger logger = Logger.getGlobal();
        logger.setLevel(Level.INFO);
        ArgumentParser parser = ArgumentParsers
                .newFor("Tulkun").build()
                .defaultHelp(true)
                .description("Distributed Dataplane Verification");
        Subparsers subparser = parser.addSubparsers().title("subcommands").help("sub-command help").dest("prog").metavar("prog");

        Subparser cbs = subparser.addParser("cbs").help("Burst update simulation evaluator. All FIBs are read at once and then verified.");
        cbs.addArgument("network").type(String.class).help("Network name. All configurations will be set automatically.");
        cbs.addArgument("-t", "--times").type(Integer.class).setDefault(1).help("The times of burst update");
        Evaluator.setParser(cbs);

        // 新增edge-connectivity命令
        Subparser edgeConnectivity = subparser.addParser("edge-connectivity")
                .help("Verify connectivity from all edge devices to directly connected agg/core devices");
        edgeConnectivity.addArgument("network")
                .type(String.class)
                .help("Network directory containing topology and configuration files");
        edgeConnectivity.addArgument("--show_result")
                .action(Arguments.storeTrue())
                .help("Show the verification results in command line");

        Namespace namespace;
        try {
            namespace = parser.parseArgs(args);
        }catch (HelpScreenException e){
            return;
        } catch (ArgumentParserException e) {
            e.printStackTrace();
            return;
        }

        String prog = namespace.getString("prog");
        Evaluator evaluator;

        // 在程序开始时初始化Node的文件写入功能（只输出一次开始提示）
        Node.initializeFromArgs(args);

        // 添加JVM关闭钩子，确保在程序退出时调用结果写入完成方法
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Node.finalizeFileWriting();
        }));

        try {
            switch (prog) {
                case "cbs": {
                    evaluator = new BurstEvaluator(namespace);
                    evaluator.start(new TopoRunner());
                    // 验证完成后，完成结果文件写入（只输出一次结束提示）
                    Node.finalizeFileWriting();
                    return;
                }
                case "edge-connectivity": {
                    String networkDir = namespace.getString("network");
                    boolean showResult = namespace.getBoolean("show_result");

                    System.out.println("启动Edge连接性验证，网络目录: " + networkDir);
                    if (showResult) {
                        System.out.println("启用结果输出模式");
                    }

                    Configuration.getConfiguration().readDirectory(networkDir, false);
                    Configuration.getConfiguration().setShowResult(showResult);

                    // 初始化EdgeConnectivityRunner的路径管理
                    EdgeConnectivityRunner.initializeFromArgs(args);

                    EdgeConnectivityRunner runner = new EdgeConnectivityRunner();
                    runner.build();
                    runner.start();
                    runner.awaitFinished();

                    // 验证完成后，完成结果文件写入（只输出一次结束提示）
                    Node.finalizeFileWriting();
                    EdgeConnectivityRunner.finalizeFileWriting();

                    runner.close();
                    return;
                }
                case "list": {
                    System.out.println("Network list:");
                    for (String n : Configuration.getNetworkList()) {
                        System.out.println("\t"+n);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("程序执行过程中发生错误: " + e.getMessage());
            e.printStackTrace();
            // 即使发生异常，也要尝试完成结果文件写入
            Node.finalizeFileWriting();
        }
    }
}