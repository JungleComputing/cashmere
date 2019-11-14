// fermi

// Avoid mangling of function names
extern "C" {
    __global__ void kmeans(int npoints, int nclusters, int nfeatures, const float* points, const float* clusters, int* pointsCluster);
}

__global__ void kmeans(int npoints, int nclusters, int nfeatures, 
        const float* points, const float* clusters, int* pointsCluster) {

    const int ttpid = threadIdx.x;
    const int wtpid = threadIdx.y;
    const int bpid = blockIdx.x;
    
    int ind = 0;
    float min_dist = 3.0E+38;

    for (int cluster = 0; cluster < nclusters; cluster++) {
    
        float dist = 0;

        for (int feature = 0; feature < nfeatures; feature++) {
	    float diff = points[1024 * bpid + (32 * wtpid + ttpid) + feature * npoints] - clusters[feature + cluster * nfeatures];
            dist = dist + diff * diff;
        }

        if (dist < min_dist) {
            min_dist = dist;
            ind = cluster;
        }
    }

    pointsCluster[1024 * bpid + (32 * wtpid + ttpid)] = ind;
}
