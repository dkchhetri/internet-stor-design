#include <stdlib.h>
#include <string.h>
#include "sym_dict.h"

int xent_cmp(xent_t *a, xent_t *b);

RB_GENERATE(rbdx, xent, rbe, xent_cmp);

unsigned int
sym_hash(char *s)
{
    unsigned int h = 5381;
    int c;
    while (c = *s++)
        h = ((h << 5) + h) + c; /* hash * 33 + c */

    return h;
}

rb_dict_xent_t *
alloc_rb_dict_xent(size_t max_ent)
{
    int i;
    rb_dict_xent_t *d;
    d = malloc(sizeof(rb_dict_xent_t));
    d->count = 0;
    RB_INIT(&d->root);
    /* allocate freelist */
    d->mem_blob = malloc(max_ent * sizeof(xent_t));
    /* force page allocation from kernel, NOW */
    memset(d->mem_blob, 0, max_ent * sizeof(xent_t));
    d->freel = d->mem_blob;
    for (i = 0; i < (max_ent - 1); i++)
        d->mem_blob[i].freel_next = &d->mem_blob[i+1];
    return d;
}

int
rbdx_insert(rb_dict_xent_t *d, const char *s, int v)
{
    xent_t *e;

    e = d->freel;
    if (e == NULL) return -1;
    d->freel = e->freel_next;

    strncpy(e->sym, s, MAX_SYM_LEN - 1);
    e->sym[MAX_SYM_LEN-1] = '\0';
#ifdef USE_SYM_HASH
    e->sym_hash = sym_hash(e->sym);
#endif
    e->val = v;

    if (RB_INSERT(rbdx, &d->root, e)) {
        d->freel = e;
        return -1;
    } else {
        d->count++;
        return 0;
    }
}

xent_t *
rbdx_lookup(rb_dict_xent_t *d, const char *s)
{
    xent_t e;

    strncpy(e.sym, s, MAX_SYM_LEN - 1);
#ifdef USE_SYM_HASH
    e.sym_hash = sym_hash(e.sym);
#endif

    return RB_FIND(rbdx, &d->root, &e);
}

xent_t *
rbdx_get_first(rb_dict_xent_t *d)
{
    return RB_MIN(rbdx, &d->root);
}

xent_t *
rbdx_get_next(rb_dict_xent_t *d, xent_t *e)
{
    return RB_NEXT(rbdx, &d->root, e);
}

int
xent_cmp(xent_t *a, xent_t *b)
{
#ifdef USE_SYM_HASH
    if (a->sym_hash == b->sym_hash)
        return strcmp(a->sym, b->sym);
    else
        return (a->sym_hash < b->sym_hash ? -1 : 1);
#else
   return strcmp(a->sym, b->sym);
#endif
}

/*vim: ts=4:et:sw=4*/
