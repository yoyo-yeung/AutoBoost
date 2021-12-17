package helper;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class Help {
    public static final String NAME = "help";

    public static Option getOption(){
        return new Option(NAME, "help");
    }

    public static void execute(Options options){
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("TestInspector", options);
    }
}
