CFLAGS=-I. -O2 -DUSE_SYM_HASH
CC=gcc

sym_test: sym_dict.c test.c sym_dict.h rb_tree.h
	$(CC) $(CFLAGS) -o $@ sym_dict.c test.c
