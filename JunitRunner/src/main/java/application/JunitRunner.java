package application;


import entities.IndexingMode;
import helper.*;
import instrumentation.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.json.simple.parser.ParseException;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Pack;
import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.options.Options;


import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JunitRunner {
    private static final Logger logger = LoggerFactory.getLogger(JunitRunner.class);
    private static final Description FAILED = Description.createTestDescription("FAILED", "FAILED");

    public static void main(String[] args) throws IOException, ParseException {
        JunitRunner runner = new JunitRunner();
        runner.processCommand(args);
        if (Properties.getInstance().getIndexingMode()== IndexingMode.USE)
            Index.getInstance().updateIndexing(new File(Properties.getInstance().getIndexFile()));
        runner.setUpSoot();
        if(Properties.getInstance().getIndexingMode() == IndexingMode.CREATE)
            Index.getInstance().storeIndexFile(Properties.getInstance().getIndexFile());
        runner.executeTests();
//        Thread.sleep(50000);
    }
    public void executeTests(){
        JUnitCore junit = new JUnitCore();
        ResultReport report = new ResultReport();
        CoverageReport coverageReport = new CoverageReport();
        junit.addListener(new RunListener() {
            public void testFailure(Failure failure){
                failure.getDescription().addChild(FAILED);
            }
            public void testFinished(Description description){
//                System.out.println(description.getMethodName());
                coverageReport.addPathCoverageInfo(description.getDisplayName(), Counter.getPathTravelled());
                coverageReport.addSetCoverageInfo(description.getDisplayName(), Counter.getOccuranceCounter());
                report.addTestResult(description.getDisplayName(), !description.getChildren().contains(FAILED));
                // reset counter
                Counter.reset();
            }
            public void testRunFinished(Result result){
                String filePath = Properties.getInstance().getTestResultFilePath();
                String filePrefix = Properties.getInstance().getTestResultPrefix();
                try {
                    report.storeResult(filePath, filePrefix);
                    coverageReport.storeCoverages(filePath, filePrefix);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        Class[] testClasses = Arrays.stream(Properties.getInstance().getTestClassNames()).map(testClassName-> {
            try {
                logger.debug("Executing tests in class "+testClassName);
                return Class.forName(testClassName);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }).toArray(Class[]:: new);
        junit.run(testClasses);
    }
    public void processCommand(String[] args){
        CommandLineParser parser = new GnuParser();
        try{
            CommandLine line = parser.parse(CommandLineParameters.getCommandLineOptions(), args);
            if(line.hasOption(Help.NAME))
                Help.execute(CommandLineParameters.getCommandLineOptions());
            CommandLineParameters.retrieveTestClassNames(line);
            CommandLineParameters.retrieveTestResultPrefix(line);
            CommandLineParameters.retrieveFilePath(line);
            CommandLineParameters.retrieveInstrumentedBinDir(line);
            CommandLineParameters.retrieveIndexInfo(line); //indexingMode + indexFile retrieve
            // expecting cpPath as current classPath includes test classes that DOES NOT require instrumentation
//            CommandLineParameters.retrieveCpPath(line);
            CommandLineParameters.retrieveInstrumentClasses(line);

        } catch (Exception e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void setUpSoot() {
        Properties properties = Properties.getInstance();
//        Options.v().set_soot_classpath(Scene.v().defaultClassPath() + File.pathSeparator + properties.getInstrumentedBinDir() + File.pathSeparator + properties.getCpPath());
        Options.v().set_soot_classpath(Scene.v().defaultClassPath() +File.pathSeparator +  properties.getInstrumentedBinDir() + File.pathSeparator  + System.getProperty("java.class.path") );
        Options.v().set_output_dir(properties.getInstrumentedBinDir());
//        Options.v().set_process_dir(Collections.singletonList(properties.getCpPath()));
        Options.v().set_keep_line_number(true);
        Options.v().setPhaseOption("jb", "use-original-names:true");
        Pack jtp = PackManager.v().getPack("jtp");
        Instrumenter instrumenter = new Instrumenter();
        jtp.add(new Transform("jtp.instrumenter", instrumenter));
//        logger.debug(String.join(",", properties.getInstrumentClasses()));
//        logger.debug(String.valueOf(properties.getInstrumentClasses().length));
        logger.debug("Instrumentation begins");
        soot.Main.main(properties.getInstrumentClasses());
    }
}
