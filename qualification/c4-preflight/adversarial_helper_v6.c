#define _GNU_SOURCE
/* V6 Route B fixed noncompiler probe. NO command, argv, environment or network target input.
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
#include <sys/vfs.h>
#include <stdint.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

/* FD 3: O_PATH root-owned sibling S directory, no write authority.
 * FD 4: sealed memfd scope record issued by fixed supervisor; not user input.
 * Both are mandatory narrow exceptions to the otherwise closed inherited set.
 * A helper report alone never authenticates these against PID1/host authority. */
struct scope_record { char magic[8]; uint64_t sdev,sino,cdev,cino,uid,gid; unsigned char nonce[16]; };
_Static_assert(sizeof(struct scope_record)==72,"scope ABI");
static struct scope_record scope;
static int scope_loaded;
static int load_scope(void) {
    struct stat ss,cs;struct statfs fs;
    int need=F_SEAL_SEAL|F_SEAL_SHRINK|F_SEAL_GROW|F_SEAL_WRITE;
    int seals=fcntl(4,F_GET_SEALS);
    if(seals<0 || (seals & need)!=need || fstat(4,&cs) || !S_ISREG(cs.st_mode) || cs.st_size!=72)return 0;
    if(pread(4,&scope,sizeof scope,0)!=sizeof scope || memcmp(scope.magic,"ARGC4V6",8))return 0;
    if(fstat(3,&ss)||fstatfs(3,&fs)||fs.f_type!=0x63677270 || !S_ISDIR(ss.st_mode)||ss.st_uid!=0||(ss.st_mode&0022))return 0;
    int flags=fcntl(3,F_GETFL);if(flags<0 || !(flags&O_PATH))return 0;
    if(ss.st_dev!=scope.sdev||ss.st_ino!=scope.sino||!scope.uid||!scope.gid)return 0;
    if(scope.sdev==scope.cdev&&scope.sino==scope.cino)return 0;
    int nonzero=0;for(int i=0;i<16;i++)nonzero|=scope.nonce[i];if(!nonzero)return 0;
    scope_loaded=1;return 1;
}
static int read_text(const char *path,char *buf,size_t size) {
    int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW); if(fd<0)return -1;
    ssize_t n=read(fd,buf,size-1); close(fd);
    if(n<0 || (size_t)n==size-1)return -1; buf[n]=0;return 0;
}
static void result(const char *name,long ret,int err) {
    struct timespec now={0};int clock_ok=clock_gettime(CLOCK_MONOTONIC,&now)==0;
    printf("{\"case\":\"%s\",\"pid\":%ld,\"ppid\":%ld,\"ret\":%ld,\"errno\":%d,\"clock_ok\":%d,\"mono_ns\":%llu,\"nonce\":\"",name,(long)getpid(),(long)getppid(),ret,err,clock_ok,(unsigned long long)now.tv_sec*1000000000ULL+(unsigned long long)now.tv_nsec);
    for(int i=0;i<16;i++)printf("%02x",scope.nonce[i]);puts("\"}");
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
    if(!scope_loaded||r!=scope.uid||gr!=scope.gid)return 0;
    if(read_text("/proc/self/cgroup",text,sizeof text)||strncmp(text,"0::/",4))return 0;
    char *nl=strchr(text,'\n');if(!nl||nl[1])return 0;*nl=0;
    if(strstr(text,"..")||strstr(text,"//"))return 0;
    char path[8192];int len=snprintf(path,sizeof path,"/sys/fs/cgroup%s",text+3);
    if(len<0||(size_t)len>=sizeof path)return 0;
    int fd=open(path,O_PATH|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return 0;
    struct stat st;struct statfs fs;int good=!fstat(fd,&st)&&!fstatfs(fd,&fs)&&fs.f_type==0x63677270&&st.st_dev==scope.cdev&&st.st_ino==scope.cino;
    close(fd);if(!good)return 0;
    return 1;
}
static int fd_inventory(void) {
    DIR *d=opendir("/proc/self/fd");if(!d)return 0;struct dirent *e;int ok=1;
    while((e=readdir(d))) {char *end;long fd=strtol(e->d_name,&end,10);if(*end)continue;
        if(fd>4 && fd!=dirfd(d))ok=0;
    }
    closedir(d);result("inherited_fds",ok?0:-1,0);return ok;
}
int main(int argc,char **argv) {
    (void)argv;setvbuf(stdout,NULL,_IONBF,0);
    sigset_t empty;sigemptyset(&empty);
    if(signal(SIGALRM,SIG_DFL)==SIG_ERR || sigprocmask(SIG_SETMASK,&empty,NULL))return 124;
    alarm(45);
    if(argc!=1 || !load_scope() || !sealed()){result("guard_refused",-1,EPERM);return 125;}
    if(!fd_inventory())return 124;
    char before[1024],after[1024];if(read_text("/proc/self/cgroup",before,sizeof before))return 124;
    int ok=1,fd=openat(3,"cgroup.procs",O_WRONLY|O_CLOEXEC|O_NOFOLLOW);
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
