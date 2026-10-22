package nachos.threads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    private class Pairing {
        int first;
        int second;
        boolean arrived;
        Condition2 cv = new Condition2(conditionLock);   
    }

    /**
     * Allocate a new Rendezvous.
     */
    private HashMap<Integer, Pairing> tagMap;
    public boolean hasArrived = false;
    public Rendezvous () {
        tagMap = new HashMap<>();
        conditionLock = new Lock();
        cv = new Condition2(conditionLock);
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {

        conditionLock.acquire();
        if(!tagMap.containsKey(tag)){
            Pairing p = new Pairing();
            // p.cv = new Condition2(conditionLock);
            p.first = value;
            p.arrived = false;
            tagMap.put(tag, p);
            
            while(!p.arrived){
                p.cv.sleep();
            }

            // B arrives
            int b = p.second;
            conditionLock.release();
            return b;
        }else{ // B has arrived
            Pairing p2 = tagMap.remove(tag);
            p2.arrived = true;
            p2.second = value;
            int a = p2.first;
            p2.cv.wake();
            conditionLock.release();
            return a;
        }
    }

    private Lock conditionLock;
    private Condition2 cv;



    private static void yieldMany(int n) {
        for (int i = 0; i < n; i++)
            KThread.yield();
    }
    
    private static KThread forkExchange(final Rendezvous r, final int tag,
            final int val, final int[] result, final boolean[] done, final int idx) {
        KThread t = new KThread(new Runnable() {
            public void run() {
                result[idx] = r.exchange(tag, val);
                done[idx] = true;
            }
        });
        t.fork();
        return t;
    }

    private static void rendezvousTestBlocks() {
        Rendezvous r = new Rendezvous();
        int[] result = new int[1];
        boolean[] done = new boolean[1];
    
        KThread a = forkExchange(r, 1, 7, result, done, 0);
        yieldMany(50);
        Lib.assertTrue(!done[0]);       
    
        r.exchange(1, 9);                
        a.join();
        Lib.assertTrue(done[0]);         
        System.out.println("rendezvousTestBlocks: passed");
    }

    private static void rendezvousTestSwapsValues() {
        Rendezvous r = new Rendezvous();
        int[] result = new int[1];
        boolean[] done = new boolean[1];
    
        KThread a = forkExchange(r, 1, 7, result, done, 0);
        int mine = r.exchange(1, 9);
        a.join();
        Lib.assertTrue(mine == 7);       
        Lib.assertTrue(result[0] == 9);  
        System.out.println("rendezvousTestSwapsValues: passed");
    }

    private static void rendezvousTestManyThreads() {
        Rendezvous r = new Rendezvous();
        int n = 6;
        int[] result = new int[n];
        boolean[] done = new boolean[n];
        KThread[] kids = new KThread[n];
    
        for (int i = 0; i < n; i++)
            kids[i] = forkExchange(r, 5, 100 + i, result, done, i);
        for (KThread k : kids) k.join();
    
        for (int i = 0; i < n; i++) {
            int j = result[i] - 100;                 
            Lib.assertTrue(j >= 0 && j < n && j != i);
            Lib.assertTrue(result[j] == 100 + i);    
        }
    
        System.out.println("rendezvousTestManyThreads: passed");
    }
    
    private static void rendezvousTestDifferentTags() {
        Rendezvous r = new Rendezvous();
        int[] result = new int[2];
        boolean[] done = new boolean[2];
    
        KThread x = forkExchange(r, 1, 11, result, done, 0);   
        KThread y = forkExchange(r, 2, 22, result, done, 1);  
        yieldMany(50);
        Lib.assertTrue(!done[0] && !done[1]);    
    
        int got1 = r.exchange(1, 33);           
        x.join();
        Lib.assertTrue(got1 == 11 && result[0] == 33);
        yieldMany(20);
        Lib.assertTrue(!done[1]);                
    
        int got2 = r.exchange(2, 44);
        y.join();
        Lib.assertTrue(got2 == 22 && result[1] == 44);
    
        System.out.println("rendezvousTestDifferentTags: passed");
    }

    private static void rendezvousTestDifferentInstances() {
        Rendezvous r1 = new Rendezvous();
        Rendezvous r2 = new Rendezvous();
        int[] result = new int[2];
        boolean[] done = new boolean[2];
    
        KThread x = forkExchange(r1, 1, 11, result, done, 0);
        KThread y = forkExchange(r2, 1, 22, result, done, 1);   
        yieldMany(50);
        Lib.assertTrue(!done[0] && !done[1]);
    
        int got1 = r1.exchange(1, 33);
        x.join();
        Lib.assertTrue(got1 == 11 && result[0] == 33);
        yieldMany(20);
        Lib.assertTrue(!done[1]);                
    
        int got2 = r2.exchange(1, 44);
        y.join();
        Lib.assertTrue(got2 == 22 && result[1] == 44);
        System.out.println("rendezvousTestDifferentInstances: passed");
    }

    public static void selfTest() {
        rendezvousTestBlocks();
        rendezvousTestSwapsValues();
        rendezvousTestManyThreads();
        rendezvousTestDifferentTags();
        rendezvousTestDifferentInstances();
    }
}
