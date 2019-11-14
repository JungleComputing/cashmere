// fermi

// Avoid mangling of function names
extern "C" {
    __global__ void matmulKernel (int n, int m, int p, float* c, const float* a, const float* b);
}

__global__ void matmulKernel (int n, int m, int p, float* c, const float* a, const float* b) {


    const int ttj = threadIdx.x;
    const int wtj = threadIdx.y;
    const int bj = blockIdx.x;
    const int bi = blockIdx.y;
    
    __shared__ float l_a[2048];
    float sums[16];
    for (int ei = 0; ei < 16; ei++) {
    
        sums[ei] = 0.0;
    }
    for (int l = 0; l < p / 128; l++) {
    
        for (int ei = 0; ei < 16; ei++) {
        
            l_a[32 * wtj + ttj + 128 * ei] = a[32 * wtj + ttj + 128 * l + (ei 
                    + 16 * bi) * (128 * (p / 128))];
        }
        __syncthreads();
        for (int k2 = 0; k2 < p / (p / 128); k2++) {
        
            const float bkj = b[128 * bj + (32 * wtj + ttj) + (l * p / (p / 
                    128) + k2) * m];
            for (int ei = 0; ei < 16; ei++) {
            
                sums[ei] += l_a[k2 + 128 * ei] * bkj;
            }
        }
        __syncthreads();
    }
    for (int ei = 0; ei < 16; ei++) {
    
        c[32 * wtj + ttj + 128 * bj + (ei + 16 * bi) * (128 * (m / 128))] += 
                sums[ei];
    }
}





