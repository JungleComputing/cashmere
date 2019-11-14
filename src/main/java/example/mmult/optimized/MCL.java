package example.mmult.optimized;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.Buffer;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.CashmereNotAvailable;


public class MCL {


    static void launchMatmulKernel(KernelLaunch kl, int n, int m, int p, 
            Buffer c, float[] a, float[] b) throws CashmereNotAvailable {
        launchMatmulKernel(kl, n, m, p, c, true, a, true, b, true);
    }


    static void launchMatmulKernel(KernelLaunch kl, int n, int m, int p, 
            Buffer c, boolean copyc, float[] a, boolean copya, float[] b, 
            boolean copyb) throws CashmereNotAvailable {
        
        kl.setArgument(n, Argument.Direction.IN);
        kl.setArgument(m, Argument.Direction.IN);
        kl.setArgument(p, Argument.Direction.IN);
        if (copyc) {
            kl.setArgument(c, Argument.Direction.INOUT);
        }
        else {
            kl.setArgumentNoCopy(c, Argument.Direction.INOUT);
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



        if (kl.getDeviceName().equals("xeon_phi")) {
            kl.launch(16 * (m / 16), 1 * (n / 128), 1 * 1, 16, 1, 1);
        }
        else if (kl.getDeviceName().equals("hd7970")) {
            kl.launch(64 * (m / 256), 4 * (n / 16), 1 * 1, 64, 4, 1);
        }
        else if (kl.getDeviceName().equals("fermi")) {
            kl.launch(32 * (m / 128), 4 * (n / 16), 1 * 1, 32, 4, 1);
        }
        else if (kl.getDeviceName().equals("xeon_e5620")) {
            kl.launch(4 * (m / 4), 1 * n, 1 * 1, 4, 1, 1);
        }
        else {
            throw new CashmereNotAvailable("no compatible device found");
        }
    }
}
