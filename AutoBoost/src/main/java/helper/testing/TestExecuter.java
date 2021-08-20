package helper.testing;

import helper.Properties;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// storing list of parameters needed to run JUnitRunner
// conduct execution
public class TestExecuter {
    private static TestExecuter singleton = new TestExecuter();
    private TestExecuter(){

    }

    public static TestExecuter getInstance(){
        return singleton;
    }


    public String composeTestCommand(String currentClassPath, String fileName) {
        StringBuilder commandLine = new StringBuilder();
        commandLine.append("java -cp ");
        commandLine.append(Properties.getInstance().getTestRunnerJarPath())
                .append(File.pathSeparator)
                .append(currentClassPath)
                .append(File.pathSeparator)
                .append(Properties.getInstance().getLinkedTestClassPaths(File.pathSeparator))
                .append(Properties.getInstance().getDependencyPaths().length==0? "": File.pathSeparator)
                .append(Properties.getInstance().getDependencyPaths().length==0? "": Properties.getInstance().getLinkedDependencyPaths(File.pathSeparator))
                .append(" ");
        commandLine.append(Properties.getInstance().getTestRunnerClass()).append(" ");
        commandLine.append("-testClasses ");
        commandLine.append(Properties.getInstance().getTestClassNames()).append(" ");
        commandLine.append("-fileName ").append(fileName).append(" ");
        commandLine.append("-resultDir ").append(Properties.getInstance().getResultDir());
        return commandLine.toString();
    }
    public Process executeCommands(String command) throws IOException {
        System.out.println("executing "+command);
        return Runtime.getRuntime().exec(command);

    }



}
