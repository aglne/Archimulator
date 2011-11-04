#include <stdlib.h>
#include <string.h>

#include "mhandle.h"
#include "buffer.h"

#define MIN(X,Y) ((X)<(Y)?(X):(Y))
#define MAX(X,Y) ((X)>(Y)?(X):(Y))

struct buffer_t {
	void *data;
	int count, size;
	int head, tail;
};




/* Private Functions */

/* grow buffer */
static int grow(struct buffer_t *buffer, int size)
{
	void *data;
	
	/* allocate new memory */
	if (size < buffer->size)
		return 0;
	data = calloc(1, size);
	if (!data)
		return 0;
	
	/* copy buffer contents */
	memcpy(data, buffer->data + buffer->head, buffer->size - buffer->head);
	memcpy(data + buffer->size - buffer->head, buffer->data, buffer->head);
	
	/* assign new fields */
	free(buffer->data);
	buffer->data = data;
	buffer->size = size;
	buffer->head = 0;
	buffer->tail = buffer->count;
	return 1;
}




/* Public Functions */

/* creation and destruction */
struct buffer_t *buffer_create(int size)
{
	struct buffer_t *buffer;
	buffer = calloc(1, sizeof(struct buffer_t));
	if (!buffer)
		return NULL;
	buffer->size = size;
	buffer->data = calloc(1, size);
	if (!buffer->data) {
		free(buffer);
		return NULL;
	}
	return buffer;
}


void buffer_free(struct buffer_t *buffer)
{
	free(buffer->data);
	free(buffer);
}


/* read */
int buffer_read(struct buffer_t *buffer, void *dest, int size)
{
	int right, left;
	
	/* compute bytes that will be read at the right/left side */
	size = MIN(size, buffer->count);
	right = MIN(buffer->size - buffer->head, size);
	left = size - right;
	
	/* read bytes */
	memcpy(dest, buffer->data + buffer->head, right);
	memcpy(dest + right, buffer->data, left);
	buffer->head = (buffer->head + size) % buffer->size;
	buffer->count -= size;
	
	return size;
}


/* write */
int buffer_write(struct buffer_t *buffer, void *dest, int size)
{
	int right, left;
	
	/* grow buffer */
	while (buffer->count + size > buffer->size)
		if (!grow(buffer, MAX(buffer->count + size, buffer->size * 2)))
			return 0;
	
	/* write */
	right = MIN(buffer->size - buffer->tail, size);
	left = size - right;
	memcpy(buffer->data + buffer->tail, dest, right);
	memcpy(buffer->data, dest + right, left);
	buffer->tail = (buffer->tail + size) % buffer->size;
	buffer->count += size;
	
	return size;
}


/* count */
int buffer_count(struct buffer_t *buffer)
{
	return buffer->count;
}
