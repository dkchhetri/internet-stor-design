#include <stdlib.h>
#include <stdio.h>
#include <string.h>
 
int
main(int argc, char **argv)
{
    int len, count;
    int r, i, j;
    char c;
    char buf[1024];
    char *g = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    int m;
    if (argc != 3)
        return(1);
    len = atoi(argv[1]);
    count = atoi(argv[2]);
    if (len > sizeof(buf))
        return(2);
    srand((unsigned int)time(0)); //Seed number for rand()
    m = strlen(g);
    for (j = 0; j < count; j++) {
        for(i = 0; i < len; i++) {
            r = rand() % m;
            buf[i] = g[r];
        }
        buf[i]='\0';
        printf("%s\n", buf);
    }
    return (0);
}
