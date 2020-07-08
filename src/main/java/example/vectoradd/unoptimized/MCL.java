package example.vectoradd.unoptimized;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.CashmereNotAvailable;


class CL {

    static void launchVectoraddKernel(KernelLaunch kl, int n, float[] c, float[] a, float[] b) throws CashmereNotAvailable {
        launchVectoraddKernel(kl, n, c, true, a, true, b, true);
    }
    
    static void launchVectoraddKernel(KernelLaunch kl, int n, float[] c, boolean copyc, float[] a, boolean copya, float[] b, boolean copyb) throws CashmereNotAvailable {
        kl.setArgument(n, Argument.Direction.IN);
        if (copyc) {
            kl.setArgument(c, Argument.Direction.OUT);
        }
        else {
            kl.setArgumentNoCopy(c, Argument.Direction.OUT);
        }
        if (copya) {
            kl.setArgument(a, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(a, Argument.Direction.IN);
        }
        if (copyb) {
            kl.setArgument(b, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(b, Argument.Direction.IN);
        }
    
        if (kl.getDeviceName().equals("fermi")) {
            int nrThreadsN = Math.min(1024, n);
            int nrBlocksN = n == 1 * nrThreadsN ?
    1 :
    n % (1 * nrThreadsN) == 0 ?
        n / (1 * nrThreadsN) :
        n / (1 * nrThreadsN) + 1
;
            int nrThreadsNrThreadsN = Math.min(32, nrThreadsN);
            int nrWarpsNrThreadsN = nrThreadsN == 1 * nrThreadsNrThreadsN ?
    1 :
    nrThreadsN % (1 * nrThreadsNrThreadsN) == 0 ?
        nrThreadsN / (1 * nrThreadsNrThreadsN) :
        nrThreadsN / (1 * nrThreadsNrThreadsN) + 1
;
	    // NOTE: added 'true' at the end to make the call synchronous.
	    // By default, the call will be asynchronous.
            kl.launch(nrThreadsNrThreadsN * nrBlocksN, nrWarpsNrThreadsN * 1, 1 * 1, nrThreadsNrThreadsN, nrWarpsNrThreadsN, 1, true);
        }
        else {
            throw new CashmereNotAvailable("no compatible device found");
        }
    }
    
}
