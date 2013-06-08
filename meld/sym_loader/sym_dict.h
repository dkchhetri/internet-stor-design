#ifndef _SYM_DICT_H
#define _SYM_DICT_H

#include <sys/types.h>
#include "rb_tree.h"

#define MAX_SYM_LEN 64
typedef struct xent {
    RB_ENTRY(xent)  rbe;
#ifdef USE_SYM_HASH
    unsigned int    sym_hash;
#endif
    int             val; 
    char            sym[MAX_SYM_LEN];
    struct xent     *freel_next;
} xent_t;

typedef struct rb_dict_xent {
    RB_HEAD(rbdx, xent)	root;
    size_t              count;
    xent_t              *mem_blob;
    xent_t              *freel;
} rb_dict_xent_t;

rb_dict_xent_t *alloc_rb_dict_xent(size_t max_ent);
int rbdx_insert(rb_dict_xent_t *d, const char *s, int v);
xent_t *rbdx_lookup(rb_dict_xent_t *d, const char *s);
xent_t *rbdx_get_first(rb_dict_xent_t *d);
xent_t *rbdx_get_next(rb_dict_xent_t *d, xent_t *e);

#endif
