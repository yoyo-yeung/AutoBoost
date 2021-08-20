package instrumentation;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class Index {
    private static final Index singleton = new Index();
    // object mapping statement to index
    private JSONObject indexObj;
    private static Logger logger = LoggerFactory.getLogger(Index.class);

    public Index() {
        indexObj = new JSONObject();
    }

    public static Index getInstance() {
        return singleton;
    }
    public void updateIndexing(File file) throws IOException, ParseException {
        logger.debug("Getting indexing from file " + file.getAbsolutePath());
        JSONParser parser = new JSONParser();
        if (! file.exists() )
            throw new IllegalArgumentException("File for indexing does not exist");
        indexObj = (JSONObject) parser.parse(new FileReader(file));
        storeIndexFile("./index_check.json");
//        logger.debug(indexObj.toJSONString());
    }

    public void storeIndexFile(String fileName) throws IOException {
        File file = new File(fileName);
        logger.debug("storing to index file " + file.getAbsolutePath());
        if(!file.exists())
            file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(indexObj.toJSONString());
        writer.flush();
        writer.close();
    }

    public int getStatementIndex(String statement) {
        if(indexObj.containsKey(statement)) {
            return Integer.parseInt(indexObj.get(statement).toString());
        }
        return -1; //no need to count if it does not exist in file
    }

    public void setStatementIndex(String statement, int index) {
        System.out.println("setting "+ index);
        indexObj.put(statement, index);
        System.out.println(indexObj);
    }
}
