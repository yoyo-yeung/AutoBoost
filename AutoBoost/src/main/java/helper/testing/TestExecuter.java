package helper.testing;

import helper.Properties;

import java.io.File;
import java.io.IOException;

// storing list of parameters needed to run JUnitRunner
// conduct execution
public class TestExecuter {
    private static final TestExecuter singleton = new TestExecuter();

    private TestExecuter() {

    }

    public static TestExecuter getInstance() {
        return singleton;
    }


    public String composeTestCommand(String currentClassPath, String fileName, boolean createIndex) {
        Properties properties = Properties.getInstance();
        StringBuilder commandLine = new StringBuilder();
        commandLine.append("java -Xmx600m -cp ");
        commandLine.append(properties.getTestRunnerJarPath())
                .append(File.pathSeparator)
                .append(currentClassPath)
                .append(File.pathSeparator)
                .append(properties.getLinkedTestClassPaths(File.pathSeparator))
                .append(properties.getDependencyPaths().length == 0 ? "" : File.pathSeparator)
                .append(properties.getDependencyPaths().length == 0 ? "" : properties.getLinkedDependencyPaths(File.pathSeparator))
                .append(" ");
        commandLine.append(properties.getTestRunnerClass()).append(" ");
        commandLine.append("-testClasses ");
        commandLine.append(properties.getTestClassNames()).append(" ");
        commandLine.append("-testResultPrefix ").append(fileName).append(" ");
        commandLine.append("-resultDir ").append(properties.getResultDir()).append(" ");
        commandLine.append("-instrumentedBinDir ").append(currentClassPath).append(" ");
        commandLine.append("-indexingMode ").append(createIndex ? "CREATE" : "USE").append(" ");
        commandLine.append("-indexFile ").append(properties.getResultDir() + File.separator + properties.getIndexFile()).append(" ");
        commandLine.append("-instrumentClasses ").append(properties.getInstrumentClasses());

        return commandLine.toString();
    }

    public Process executeCommands(String command) throws IOException {
        System.out.println("executing " + command);
        return Runtime.getRuntime().exec(command);

    }


}
