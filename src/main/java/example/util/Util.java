package example.util;

import java.util.Random;
import java.util.ArrayList;

import ibis.constellation.AbstractContext;
import ibis.constellation.Context;
import ibis.constellation.StealPool;

public class Util {
    private static final ArrayList<AbstractContext> globalContexts = new ArrayList<AbstractContext>();

    private static final ArrayList<AbstractContext> localContexts = new ArrayList<AbstractContext>();

    private static final ArrayList<StealPool> globalPools = new ArrayList<StealPool>();

    private static final ArrayList<StealPool> localPools = new ArrayList<StealPool>();

    private static final String localBase;

    static {
        Random r = new Random();
        localBase = "LOCAL-" + r.nextInt() + "-";
    };

    // Prevent construction
    private Util() {
    }

    public static synchronized AbstractContext localContext(int num) {
        if (num >= localContexts.size()
                || localContexts.get(num) == null) {
            while (num >= localContexts.size()) {
                localContexts.add(null);
            }
            localContexts.set(num, new Context(localBase + num));
                }
        return localContexts.get(num);
    }

    public static synchronized AbstractContext globalContext(int num) {
        if (num >= globalContexts.size()
                || globalContexts.get(num) == null) {
            while (num >= globalContexts.size()) {
                globalContexts.add(null);
            }
            globalContexts.set(num, new Context("GLOBAL-" + num));
                }
        return globalContexts.get(num);
    }

    public static synchronized StealPool globalPool(int num) {
        if (num >= globalPools.size() || globalPools.get(num) == null) {
            while (num >= globalPools.size()) {
                globalPools.add(null);
            }
            globalPools.set(num, new StealPool("GLOBAL-" + num));
        }
        return globalPools.get(num);
    }

    public static synchronized StealPool localPool(int num) {
        if (num >= localPools.size() || localPools.get(num) == null) {
            while (num >= localPools.size()) {
                localPools.add(null);
            }
            localPools.set(num, new StealPool(localBase + num));
        }
        return localPools.get(num);
    }

    public static String globalName(int num) {
        return "GLOBAL-" + num;
    }
}
