#define _GNU_SOURCE
/* Fixed noncompiler probe. NO command, argv, environment or network target input.
 * Never run outside a separately authorized sealed supervisor. */
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/capability.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#define PARENT "/sys/fs/cgroup/argentum-c4-v5"
#define MEMBER "0::/argentum-c4-v5/argentum-"
static int read_text(const char *path,char *buf,size_t size) {
    int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW); if(fd<0)return -1;
    ssize_t n=read(fd,buf,size-1); close(fd);
    if(n<0 || (size_t)n==size-1)return -1; buf[n]=0;return 0;
}
static void result(const char *name,long ret,int err) {
    printf("{\"case\":\"%s\",\"pid\":%ld,\"ret\":%ld,\"errno\":%d}\n",name,(long)getpid(),ret,err);
}
static int deny(const char *name,long ret) {
    int e=errno;result(name,ret,e);return ret==-1 && (e==EPERM || e==EACCES);
}
static int sealed(void) {
    char text[8192];uid_t r,e,s;gid_t gr,ge,gs;
    if(getresuid(&r,&e,&s)||getresgid(&gr,&ge,&gs)||!r||r!=e||r!=s||!gr||gr!=ge||gr!=gs)return 0;
    if(getgroups(0,NULL)!=0 || prctl(PR_GET_NO_NEW_PRIVS,0,0,0,0)!=1 || prctl(PR_GET_SECCOMP,0,0,0,0)!=2)return 0;
    if(read_text("/proc/self/status",text,sizeof text))return 0;
    const char *keys[]={"CapInh:","CapPrm:","CapEff:","CapBnd:","CapAmb:"};
    for(size_t i=0;i<5;i++){char *p=strstr(text,keys[i]);unsigned long long v=1;if(!p||sscanf(p+strlen(keys[i]),"%llx",&v)!=1||v)return 0;}
    if(read_text("/proc/self/cgroup",text,sizeof text)||strncmp(text,MEMBER,strlen(MEMBER)))return 0;
    const char *tail=text+strlen(MEMBER);size_t n=strcspn(tail,"\n");
    if(n!=36 || tail[n]!='\n' || tail[n+1]!=0)return 0;
    for(size_t i=0;i<n;i++)if(!(tail[i]=='-' || (tail[i]>='0'&&tail[i]<='9') || (tail[i]>='a'&&tail[i]<='f')))return 0;
    return 1;
}
static int fd_inventory(void) {
    DIR *d=opendir("/proc/self/fd");if(!d)return 0;struct dirent *e;int ok=1;
    while((e=readdir(d))) {char *end;long fd=strtol(e->d_name,&end,10);if(*end)continue;
        if(fd>2 && fd!=dirfd(d))ok=0;
    }
    closedir(d);result("inherited_fds",ok?0:-1,0);return ok;
}
int main(int argc,char **argv) {
    (void)argv;setvbuf(stdout,NULL,_IONBF,0);
    sigset_t empty;sigemptyset(&empty);
    if(signal(SIGALRM,SIG_DFL)==SIG_ERR || sigprocmask(SIG_SETMASK,&empty,NULL))return 124;
    alarm(45);
    if(argc!=1 || !sealed()){result("guard_refused",-1,EPERM);return 125;}
    if(!fd_inventory())return 124;
    char before[1024],after[1024];if(read_text("/proc/self/cgroup",before,sizeof before))return 124;
    int ok=1,fd=open(PARENT "/cgroup.procs",O_WRONLY|O_CLOEXEC|O_NOFOLLOW);
    if(fd<0)ok &= deny("migration",-1);
    else {char pid[32];int n=snprintf(pid,sizeof pid,"%ld",(long)getpid());errno=0;long r=write(fd,pid,(size_t)n);ok &= deny("migration",r);close(fd);}
    if(read_text("/proc/self/cgroup",after,sizeof after))return 124;
    result("membership_unchanged",strcmp(before,after)?-1:0,0);ok &= !strcmp(before,after);
    errno=0;ok &= deny("setuid",setresuid(0,0,0));errno=0;ok &= deny("setgid",setresgid(0,0,0));
    struct __user_cap_header_struct h={_LINUX_CAPABILITY_VERSION_3,0};
    struct __user_cap_data_struct c[2]={{0}};c[0].effective=c[0].permitted=1U<<CAP_SYS_ADMIN;
    errno=0;ok &= deny("capset",syscall(SYS_capset,&h,c));
    errno=0;ok &= deny("unshare",unshare(CLONE_NEWNET));
    int ns=open("/proc/self/ns/net",O_RDONLY|O_CLOEXEC);if(ns<0)return 124;
    errno=0;ok &= deny("setns",setns(ns,CLONE_NEWNET));close(ns);
    errno=0;ok &= deny("mount",mount("none","/tmp/argentum-v5-mount","tmpfs",0,NULL));
    errno=0;fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);ok &= deny("unix_socket",fd);if(fd>=0)close(fd);
    int pair[2];errno=0;int rc=socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair);ok &= deny("unix_socketpair",rc);if(!rc){close(pair[0]);close(pair[1]);}
    /* No connect/bind/send. Namespace/route evidence must come from supervisor.
       Reading interfaces here is diagnostic only, never proof of daemon isolation. */
    char net[4096];int nr=read_text("/proc/net/dev",net,sizeof net);result("network_inventory",nr,0);
    if(!nr){char *line=strtok(net,"\n");while(line){char *colon=strchr(line,':');if(colon){*colon=0;char *p=line;while(*p==' ')p++;if(strcmp(p,"lo"))ok=0;}line=strtok(NULL,"\n");}}
    else ok=0;
    if(!sealed())ok=0;result("post_seal",ok?0:-1,0);if(!ok)return 123;
    int ready[2];if(pipe2(ready,O_CLOEXEC))return 124;
    pid_t child=fork();if(child<0)return 124;
    if(!child){
        close(ready[0]);pid_t intermediate=getpid();pid_t grand=fork();if(grand<0)_exit(124);if(grand>0)_exit(0);
        alarm(40); /* safety fallback, not accepted as cgroup.kill evidence */
        struct timespec delay={0,10000000};int tries=100;
        while(getppid()==intermediate && tries--)nanosleep(&delay,NULL);
        int good=getppid()!=intermediate && sealed();
        result("reparent_descendant",good?0:-1,0);result("persistent_descendant",0,0);
        char token=good?'R':'X';if(write(ready[1],&token,1)!=1)_exit(124);close(ready[1]);
        for(;;)pause();
    }
    close(ready[1]);int status=0;if(waitpid(child,&status,0)!=child || status!=0)return 124;
    char token=0;ssize_t n=read(ready[0],&token,1);close(ready[0]);
    if(n!=1 || token!='R')return 124;
    result("launcher_complete",0,0);return 0;
}
