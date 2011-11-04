/* For copyright information, see olden_v1.0/COPYRIGHT */
#ifndef HASH_H
#define HASH_H

#include "stdio.h"

#define assert(num,a) if (!(a)) {printf("Assertion failure:%d in hash\n",num); exit(-1);}

typedef struct hash_entry {
  unsigned int key;
  void *entry;
  struct hash_entry *next;
} *HashEntry;

typedef struct hash {
  HashEntry *array;
  int (*mapfunc)(unsigned int);
  int size;
} *Hash;

Hash MakeHash(int size, int (*map)(unsigned int));
void *HashLookup(unsigned int key, Hash hash);
void HashInsert(void *entry,unsigned int key, Hash hash);
void HashDelete(unsigned int key, Hash hash);

#endif
