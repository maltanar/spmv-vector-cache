
#ifndef MALLOC_ALIGNED_H_
#define MALLOC_ALIGNED_H_

// source: http://stackoverflow.com/questions/6563120/what-does-posix-memalign-memalign-do

void *malloc_aligned(size_t alignment, size_t bytes);
void free_aligned(void *raw_data);


#endif /* MALLOC_ALIGNED_H_ */
