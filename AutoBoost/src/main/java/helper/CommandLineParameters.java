package helper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CommandLineParameters {
    public static Options getCommandLineOptions(){
        Options options = new Options();
        Option help = Help.getOption();
        Option fixedCode = new Option("fixedPath", true, "Required, Path for binaries of fixed version of code");
        Option plausibleFixes = new Option("plausibleFixesPaths", true, "Required, List of paths to binaries of other plausible but unaccepted fixes, separated with File.pathSeparator");
        // may remove this after confirmed on instrument modified class vs related classes vs all classes
        Option instrumentClasses = new Option("instrumentClasses", true, "Temp. Required. List of classes to instrument, separated by , ");
        Option testClassPaths = new Option("testClassPaths", true, "Required, List of paths to binaries of tests generated, separated with File.pathSeparator");
        Option testClassNames = new Option("testClassNames", true, "Required, List of test class names, separated by ,");
        Option testRunnerJar = new Option("testRunnerJar", true, "Required, Path for test runner jar file");
        Option testRunnerClass = new Option("testRunnerClass", true, "Optional, Class of test runner, default: "+Properties.DEFAULT_TEST_RUNNER_CLASS);
        Option dependencyPaths = new Option("dependencyPaths", true, "Optional, Paths for dependencies");
        Option resultDir = new Option("resultDir", true, "Optional, Path to store result of test executions, default :"+Properties.DEFAULT_RESULT_DIR);
        options.addOption(help);
        options.addOption(fixedCode);
        options.addOption(plausibleFixes);
        options.addOption(instrumentClasses);
        options.addOption(testClassPaths);
        options.addOption(testClassNames);
        options.addOption(testRunnerJar);
        options.addOption(testRunnerClass);
        options.addOption(dependencyPaths);
        options.addOption(resultDir);
        return options;
    }
    public static void handleFixedPath(CommandLine line) throws MissingArgumentException, IOException {

        if(!line.hasOption("fixedPath"))
            throw new MissingArgumentException("Missing fixedPath argument");
        File dir = new File(line.getOptionValue("fixedPath"));
        if(!dir.exists())
            throw new IllegalArgumentException("FixedPath input does not exist");
        // store the files to tempbin for manipulation
        File destDir = new File(Properties.TEMP_BIN, Properties.FIXED_FILE_PREFIX);
        if(!destDir.exists())
            destDir.mkdirs();
            FileUtils.copyDirectory(dir, destDir);
        Properties.getInstance().setFixedClassPath(destDir.getAbsolutePath());
    }

    public static void handlePlausibleFixesPaths(CommandLine line) throws MissingArgumentException, IOException {
        if(!line.hasOption("plausibleFixesPaths"))
            throw new MissingArgumentException("Missing plausibleFixesPaths argument");
        String[] dirs = line.getOptionValue("plausibleFixesPaths").split(File.pathSeparator);
        List<String> destDirs = new ArrayList<String>();
        File dir;
        File destDir;
        for(String dirPath: dirs){
            dir = new File(dirPath);
            if(!dir.exists())
                throw new IllegalArgumentException("plausibleFixesPaths "+dirPath+" does not exist");
            destDir = new File(Properties.TEMP_BIN, Properties.PATCH_FILE_PREFIX+(destDirs.size()+1));
            if(!destDir.exists())
                destDir.mkdirs();
            FileUtils.copyDirectory(dir, destDir);
            destDirs.add(destDir.getAbsolutePath());
        }
        Properties.getInstance().setUnacceptedClassPaths(destDirs.toArray(new String[0]));
    }
    public static void handleTestClassPaths(CommandLine line) throws MissingArgumentException {
        // expect only 1 directory, but allow multiple just in case
        if(!line.hasOption("testClassPaths"))
            throw new MissingArgumentException("Missing testClassPath argument");
        String[] dirs = line.getOptionValue("testClassPaths").split(File.pathSeparator);
        File dir;
        for(String dirPath: dirs){
            dir = new File(dirPath);
            if(!dir.exists())
                throw new IllegalArgumentException("testClassPaths "+dirPath+" does not exist");
        }
        Properties.getInstance().setTestClassPaths(dirs);
    }

    public static void handleTestClassNames(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("testClassNames"))
            throw new MissingArgumentException("Missing testClassNames argument");
        Properties.getInstance().setTestClassNames(line.getOptionValue("testClassNames"));
    }

    public static void handleTestRunnerJarPath(CommandLine line) throws MissingArgumentException {
        if(!line.hasOption("testRunnerJar"))
            throw new MissingArgumentException("Missing testRunnerJar argument");
        File jarFile = new File(line.getOptionValue("testRunnerJar"));
        if(!jarFile.exists())
            throw new IllegalArgumentException("testRunnerJar input invalid");
        Properties.getInstance().setTestRunnerJarPath(line.getOptionValue("testRunnerJar"));
    }

    public static void handleTestRunnerClass(CommandLine line) {
        if(!line.hasOption("testRunnerClass"))
            Properties.getInstance().setTestRunnerClass(Properties.DEFAULT_TEST_RUNNER_CLASS);
        else
            Properties.getInstance().setTestRunnerClass(line.getOptionValue("testRunnerClass"));
    }

    public static void handleDependencyPaths(CommandLine line){
        if(!line.hasOption("dependencyPaths"))
            Properties.getInstance().setDependencyPaths(new String[0]);
        else {
            String[] dirPaths = line.getOptionValue("dependencyPaths").split(File.pathSeparator);
            File dir;
            for (String dirPath : dirPaths) {
                dir = new File(dirPath);
                if(!dir.exists())
                    throw new IllegalArgumentException("dependencyPath "+dirPath + "does not exist");
            }
            System.out.println("dependencyPaths: "+dirPaths[0]);
            Properties.getInstance().setDependencyPaths(dirPaths);
        }
    }

    public static void handleResultDir(CommandLine line) {
        File dir;
        if(!line.hasOption("resultDir")){
            dir = new File(new File("").getAbsolutePath(), Properties.DEFAULT_RESULT_DIR);
        }
        else
            dir = new File(line.getOptionValue("resultDir"));
        if(!dir.exists())
            dir.mkdirs();
        Properties.getInstance().setResultDir(dir.getAbsolutePath());

    }

    public static void handleInstrumentClasses(CommandLine line ) throws MissingArgumentException {
        if(! line.hasOption("instrumentClasses"))
            throw new MissingArgumentException("Missing instrumentClasses argument");
        Properties.getInstance().setInstrumentClasses(line.getOptionValue("instrumentClasses"));
    }

}
