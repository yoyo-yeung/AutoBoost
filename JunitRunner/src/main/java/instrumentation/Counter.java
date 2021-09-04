package instrumentation;

import java.util.ArrayList;
import java.util.HashMap;

public class Counter {
    private static ArrayList<Integer> indexList = new ArrayList<>(); // a list of all statements / shared statement indexes
    private static HashMap<Integer, Integer> occuranceCounter = new HashMap<>();
    private static ArrayList<Integer> pathTravelled = new ArrayList<>();
    private static int total = 0;

    public static void reset() {
        occuranceCounter = new HashMap<>();
        pathTravelled = new ArrayList<>();
    }


    public static void addOccurance(int index, int amt) {
        occuranceCounter.put(index, occuranceCounter.getOrDefault(index, 0) + amt);  // for set
        pathTravelled.add(index);  // for path
    }

    public static ArrayList<Integer> getIndexList() {
        return indexList;
    }

    public static HashMap<Integer, Integer> getOccuranceCounter() {
        return occuranceCounter;
    }

    public static ArrayList<Integer> getPathTravelled() {
        return pathTravelled;
    }

    // may be used to add new index (if CREATE mode used)
    public static int getTotal() {
        return total;
    }

    public static int getNewIndex() {
        total+= 1;
//        System.out.println(total);
        indexList.add(total);
        return total;
    }
}
