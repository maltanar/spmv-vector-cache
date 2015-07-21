#include <stdlib.h>
#include <stdio.h>

// source: http://stackoverflow.com/questions/6563120/what-does-posix-memalign-memalign-do

void *malloc_aligned(size_t alignment, size_t bytes)
{
    // we need to allocate enough storage for the requested bytes, some
    // book-keeping (to store the location returned by malloc) and some extra
    // padding to allow us to find an aligned byte.  im not entirely sure if
    // 2 * alignment is enough here, its just a guess.
    const size_t total_size = bytes + (2 * alignment) + sizeof(size_t);

    // use malloc to allocate the memory.
    char *data = (char*) malloc(sizeof(char) * total_size);

    if (data)
    {
        // store the original start of the malloc'd data.
        const void * const data_start = data;

        // dedicate enough space to the book-keeping.
        data += sizeof(size_t);

        // find a memory location with correct alignment.  the alignment minus
        // the remainder of this mod operation is how many bytes forward we need
        // to move to find an aligned byte.
        const size_t offset = alignment - (((size_t)data) % alignment);

        // set data to the aligned memory.
        data += offset;

        // write the book-keeping.
        size_t *book_keeping = (size_t*)(data - sizeof(size_t));
        *book_keeping = (size_t)data_start;
    }

    return data;
}

void free_aligned(void *raw_data)
{
    if (raw_data)
    {
        char *data = (char*) raw_data;

        // we have to assume this memory was allocated with malloc_aligned.
        // this means the sizeof(size_t) bytes before data are the book-keeping
        // which points to the location we need to pass to free.
        data -= sizeof(size_t);

        // set data to the location stored in book-keeping.
        data = (char*)(*((size_t*)data));

        // free the memory.
        free(data);
    }
}
