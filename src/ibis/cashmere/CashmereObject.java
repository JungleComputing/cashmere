/* $Id: CashmereObject.java 6429 2007-09-20 13:46:01Z ceriel $ */

package ibis.cashmere;

import ibis.cashmere.impl.Cashmere;

import java.io.Serializable;

/**
 * This is the magic class that should be extended by objects that implement
 * spawnable methods. When the program is not rewritten by the Cashmere frontend,
 * the methods described here are basically no-ops, and the program will run
 * sequentially. When the program is rewritten by the Cashmere frontend, calls to
 * spawnable methods, and calls to {@link #sync()}and {@link #abort()}will be
 * rewritten.
 */
public class CashmereObject implements java.io.Serializable {

    /** 
     * Generated
     */
    private static final long serialVersionUID = -3958487192660018892L;

    /**
     * Prevents constructor from being public.
     */
    protected CashmereObject() {
        // nothing here
    }

    /**
     * Waits until all spawned methods in the current method are finished.
     */
    public void sync() {
        /* do nothing, bytecode is rewritten to handle this */
    }

    /**
     * Recursively aborts all methods that were spawned by the current method
     * and all methods spawned by the aborted methods.
     */
    public void abort() {
        /* do nothing, bytecode is rewritten to handle this */
    }

    /**
     * Pauses Cashmere operation. This method can optionally be called before a
     * large sequential part in a program. This will temporarily pause Cashmere's
     * internal load distribution strategies to avoid communication overhead
     * during sequential code.
     */
    public static void pause() {
        Cashmere.pause();
    }

    /**
     * Resumes Cashmere operation. This method can optionally be called after a
     * large sequential part in a program.
     */
    public static void resume() {
        Cashmere.resume();
    }

    /**
     * Returns whether it might be useful to spawn more Cashmere jobs. If there is
     * enough work in the system to keep all processors busy, this method
     * returns false.
     * 
     * @return <code>true</code> if it might be useful to spawn more
     *         invocations, false if there is enough work in the system.
     */
    public static boolean needMoreJobs() {
        return Cashmere.needMoreJobs();
    }

    /**
     * Returns whether the current Cashmere job was generated by the machine
     * it is running on. Cashmere jobs can be distributed to remote machines by
     * the Cashmere runtime system, in which case this method returns false.
     * 
     * @return <code>true</code> if the current invocation is not stolen from
     *         another processor.
     */
    public static boolean localJob() {
        return Cashmere.localJob();
    }

    /**
     * Creates and returns a deep copy of the specified object.
     * @param o the object to be copied
     * @return the copy.
     */
    public static Serializable deepCopy(Serializable o) {
        return Cashmere.deepCopy(o);
    }
}