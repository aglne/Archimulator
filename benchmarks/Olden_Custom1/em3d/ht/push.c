#include "em3d.h"

#include "push.h"

int volatile i_sem = 0;

node_t** volatile curr_pointer_addr;

extern int d_nodes;

int volatile push_flag = -1;

inline void push_thread_func()
{
  volatile int i = 0,degree = d_nodes;
  node_t* volatile curr_p;
  node_t** volatile  to_curr_p;

  //////////////////////////

  volatile int tmp_i, tmp_j, tmp_count;
  volatile node_t * volatile tmp_other_node;

  double ** volatile tmp_otherlist;
  double * volatile tmp_value;

  volatile double tmp;
  volatile int tmp_degree;

  volatile node_t * volatile node_index;
  volatile node_p * volatile tmp_cur_node;

  int j;

  int lookahead = LOOKAHEAD;
  int stride = STRIDE;

  //////////////////////////

  while (1) {
    while (i_sem<=0);
    i_sem--;

    if(push_flag == 1) {
      to_curr_p = (node_t**)curr_pointer_addr;
      curr_p = (node_t*)(*to_curr_p);
      while (curr_p) {
        for(j=0; j<lookahead && curr_p && push_flag; curr_p=curr_p->next);

        while (curr_p && i++<stride) {
          for (j = 0;j<degree;j++) {
            volatile int count,thecount;
            void* volatile pushaddr;
            node_t * volatile temp_other = curr_p->to_nodes[j];
            double volatile **otherlist;
            volatile double *value = curr_p->value;
            if (temp_other) {
              count = temp_other->from_length;
              otherlist = temp_other->from_values;
            }
            pushaddr = &(otherlist[count]);
            __builtin_prefetch(pushaddr);
          }
          curr_p = curr_p->next;
        }
        i = 0;
        curr_p = (node_t*)(*to_curr_p);
        if (curr_p) curr_p = curr_p->next;
      }
    }
    else {
        continue;
    }
  }
}

inline void push_start(void* curr_addr)
{
  curr_pointer_addr  = (node_t**)curr_addr;
  push_flag = 1;
  i_sem++;
}
