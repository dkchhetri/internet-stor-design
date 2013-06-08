#include <stdio.h>
#include <fcntl.h>
#include "sym_dict.h"

rb_dict_xent_t *
load_symbols(char *file)
{
    FILE *f = fopen(file, "r");
    char b[512];
    rb_dict_xent_t *d;
    int l;

    d = alloc_rb_dict_xent(1e6);
    if (f == NULL || d == NULL) return NULL;

    while(fgets(b, sizeof(b), f)) {
        l = strnlen(b, sizeof(b)-1);
	b[l-1] = '\0';
        rbdx_insert(d, b, l);
    }
    fclose(f);
    return d;
}

void
dump_symbols(rb_dict_xent_t *d)
{
    xent_t *e;
    e = rbdx_get_first(d);
    while (e) {
        printf("%s => %d\n", e->sym, e->val);
        e = rbdx_get_next(d, e);
    }
}

void
do_lookup(rb_dict_xent_t *d, const char *file)
{
    FILE *f = fopen(file, "r");
    char b[512];
    xent_t *e;
    int found = 0, not_found = 0;
    int l;

    while(fgets(b, sizeof(b), f)) {
        l = strnlen(b, sizeof(b)-1);
	b[l-1] = '\0';
    	e = rbdx_lookup(d, b);
        if (e)
            found++;
        else
            not_found++;
    }
    fclose(f);
    printf("Found: %d, Not Found: %d\n", found, not_found);
}

void
do_file_loading(const char *file)
{
    FILE *f = fopen(file, "r");
    char b[512];
    int l;

    while(fgets(b, sizeof(b), f)) {
        l = strnlen(b, sizeof(b)-1);
	b[l-1] = '\0';
    }
    fclose(f);
}

int
main(int argc, char **argv)
{
    rb_dict_xent_t *d;
    char *sym_file = argv[1];

    if (argc != 2)
        return 1;

    d = load_symbols(sym_file);
//    dump_symbols(d);
    do_lookup(d, sym_file);
    do_lookup(d, sym_file);
    do_lookup(d, sym_file);
    do_lookup(d, sym_file);
//    do_file_loading(sym_file);
//    do_file_loading(sym_file);

    return 0;
}

/*vim: ts=4:et:sw=4*/
