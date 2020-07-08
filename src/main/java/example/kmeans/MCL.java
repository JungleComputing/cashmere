package example.kmeans;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.CashmereNotAvailable;


class MCL {


    static void launchKmeans_kernelKernel(KernelLaunch kl, int npoints, int 
            nclusters, int nfeatures, float[] points, float[] clusters, int[] 
            pointsCluster) throws CashmereNotAvailable {
        launchKmeans_kernelKernel(kl, npoints, nclusters, nfeatures, points, 
                true, clusters, true, pointsCluster, true);
    }


    static void launchKmeans_kernelKernel(KernelLaunch kl, int npoints, int 
            nclusters, int nfeatures, float[] points, boolean copypoints, 
            float[] clusters, boolean copyclusters, int[] pointsCluster, 
            boolean copypointsCluster) throws CashmereNotAvailable {
        
        kl.setArgument(npoints, Argument.Direction.IN);
        kl.setArgument(nclusters, Argument.Direction.IN);
        kl.setArgument(nfeatures, Argument.Direction.IN);
        if (copypoints) {
            kl.setArgument(points, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(points, Argument.Direction.IN);
        }
        if (copyclusters) {
            kl.setArgument(clusters, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(clusters, Argument.Direction.IN);
        }
        if (copypointsCluster) {
            kl.setArgument(pointsCluster, Argument.Direction.OUT);
        }
        else {
            kl.setArgumentNoCopy(pointsCluster, Argument.Direction.OUT);
        }



        if (kl.getDeviceName().equals("xeon_phi")) {
            kl.launch(16 * (npoints / 16), 1 * 1, 1 * 1, 16, 1, 1);
        }
        else if (kl.getDeviceName().equals("hd7970")) {
            kl.launch(64 * (npoints / 256), 4 * 1, 1 * 1, 64, 4, 1);
        }
        else if (kl.getDeviceName().equals("fermi")) {
            kl.launch(32 * (npoints / 1024), 32 * 1, 1 * 1, 32, 32, 1);
        }
        else if (kl.getDeviceName().equals("xeon_e5620")) {
            kl.launch(4 * (npoints / 4), 1 * 1, 1 * 1, 4, 1, 1);
        }
        else {
            throw new CashmereNotAvailable("no compatible device found");
        }
    }
}
