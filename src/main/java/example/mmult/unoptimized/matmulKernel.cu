// fermi

// Avoid mangling of function names
extern "C" {
    __global__ void matmulKernel (int n, int m, int p, float* c, const float* a, const float* b);
}

__global__ void matmulKernel (int n, int m, int p, float* c, const float* a, const float* b) {


    const int ttj = threadIdx.x;
    const int wtj = threadIdx.y;
    const int bj = blockIdx.x;
    const int i = blockIdx.y;
    
    const int nrThreadsNrThreadsM = 32;
    const int nrWarpsNrThreadsM = 32;
    const int tj = 32 * wtj + ttj;
    const int j = 1024 * bj + (32 * wtj + ttj);
    float sum = 0.0;
    for (int k = 0; k < p; k++) {
    
        sum = sum + a[k + i * p] * b[1024 * bj + (32 * wtj + ttj) + k * m];
    }
    c[1024 * bj + (32 * wtj + ttj) + i * m] += sum;
}
