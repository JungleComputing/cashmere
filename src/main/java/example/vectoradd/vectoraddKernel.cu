// fermi


// Avoid mangling of function names
extern "C" {
    __global__ void vectoraddKernel(const int n, float* c, const float* a, const float* b);
}

__global__ void vectoraddKernel(const int n, float* c, const float* a, const float* b) {
    const int bi = blockIdx.x;
    const int wti = threadIdx.y;
    const int tti = threadIdx.x;

    const int nrThreadsN = min(1024, n);
    const int nrBlocksN = n == 1 * nrThreadsN ?
        1 :
        n % (1 * nrThreadsN) == 0 ?
            n / (1 * nrThreadsN) :
            n / (1 * nrThreadsN) + 1
    ;
    const int nrThreadsNrThreadsN = min(32, nrThreadsN);
    const int nrWarpsNrThreadsN = nrThreadsN == 1 * nrThreadsNrThreadsN ?
        1 :
        nrThreadsN % (1 * nrThreadsNrThreadsN) == 0 ?
            nrThreadsN / (1 * nrThreadsNrThreadsN) :
            nrThreadsN / (1 * nrThreadsNrThreadsN) + 1
    ;
    const int ti = wti * (1 * nrThreadsNrThreadsN) + tti;
    if (ti < nrThreadsN) {
        const int i = bi * (1 * nrThreadsN) + ti;
        if (i < n) {
            c[i] = a[i] + b[i];
        }
    }
}
