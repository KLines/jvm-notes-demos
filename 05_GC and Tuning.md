# GC和GC Tuning

作者：马士兵教育 http://mashibing.com

### GC的基础知识

#### 1.什么是垃圾

> C语言申请内存：malloc free
>
> C++： new delete
>
> c/C++ 手动回收内存
>
> Java: new ？
>
> 自动内存回收，编程上简单，系统不容易出错，手动释放内存，容易出两种类型的问题：
>
> 1. 忘记回收
> 2. 多次回收

没有任何引用指向的一个对象或者多个对象（循环引用）

#### 2.如何定位垃圾

1. 引用计数（ReferenceCount）Python用的就是这一种
2. 根可达算法(RootSearching) Hotspot使用这一种，运行效率比Python高好多，其中就有垃圾回收算法的功劳
   GC Roots包括：线程变量、静态变量、常量池、JNI指针（JVM stack，native method stack，run-time constant pool，static references in method area，Clazz），
   通过root找不到的都算是垃圾 

#### 3.常见的垃圾回收算法

1. 标记清除(mark sweep) - 找到垃圾，然后把它给清了，也就是标记成可用。存活对象很多的时候效率比较高。位置不连续 产生碎片 效率偏低（两遍扫描，先找出不可回收的，再标记那些可回收）
2. 拷贝算法 (copying) - 只用一半内存，GC的时候把有用的对象拷贝到另一半连续地存放，把这一半整个清除。同时，以后再分配内存的时候往另一半分配。没有碎片，浪费空间。适用于存活对象比较少的情况（伊甸园区）
3. 标记压缩(mark compact) - 在做标记清除的同时做了一次压缩整理。没有碎片，后面再申请大对象的时候好找地方安排。效率偏低（两遍扫描，指针需要调整，多线程要进行同步
   单线程又太慢）找垃圾的效率大家都一样

#### 4.JVM堆内存分代模型（用于分代垃圾回收算法，以1.8为准讲解）

1. 部分垃圾回收器使用的模型

   > 除G1、Epsilon、 ZGC、 Shenandoah等新的GC之外的GC都是使用逻辑分代模型
   >
   > G1是逻辑分代，物理不分代。同一块内存可以在eden和old区的角色之间转换 （现在调优可以简简单单增大内存并且指定G1）
   > PN+CMS的吞吐量比G1大（G1比PN+CMS少15%，就好像好多人盯着天安门广场的每一块砖，有垃圾就回收），G1的响应时间比PN+CMS号，
   > 同时它很少有CMS的问题
   > 除此之外不仅逻辑分代，而且物理分代
2、3、4中具体讲述分代的GC的特点
2. 新生代 + 老年代（前两者都在堆里面） + 永久代（1.7）Perm Generation/ 元数据区or元空间(1.8) Metaspace
   1. 永久代 元数据 - 都是装各种各样的**Class对象**的, 还有方法、代码编译完的信息、JIT代码、二进制字节码（1.7之前这里面也会产生溢出，1.8之后就没有限制了）
   2. 永久代必须指定大小限制 ，元数据可以设置，也可以不设置，无上限（受限于物理内存）。如果有限制，spring动态代理的时候会生成好多Class文件，到时候会有永久带的内存溢出
   3. 字符串常量 1.7 - 永久代，1.8之后 - 堆（**并不是在元数据区**）
   4. MethodArea逻辑概念 - 永久代（1.7）、元数据(1.8)，并不是一个物理上的分区
永久带和元数据区：大小可以指定，但是一旦指定好了就不能改了，容易产生溢出；元数据区就不受JVM的管理了，而是被OS管理  
   对象分配（不重要）：
    1. 首先试图栈上分配，这个概念是Java对标C语言的struct可以在栈上分配的功能而推出的。好处是不用麻烦垃圾收集器
    2. 然后是线程本地分配（Thread Local Allocation Buffer）很多线程争着往eden区域里申请内存new对象，这样会产生争用，要进行同步。现在
       给每个线程默认1%的eden空间。这个适合小对象
   
   
3. 新生代 = Eden + 2个suvivor区 (Eden : s1 : s2 = 8 : 1 : 1)
   1. YGC（Young GC）回收之后，大多数的对象会被回收，活着的进入s0
   2. 再次YGC，活着的对象eden + s0 -> s1
   3. 再次YGC，eden + s1 -> s0
   4. 年龄足够 -> 老年代 （古老的GC 15岁 CMS 6岁）
   5. s区装不下 -> 老年代
   6. 新对象new出来应该放入eden，但是如果太大，则直接进入老年代
老年代就是个兜底的
   
4. 老年代
   1. 顽固分子 在s0和s1之间已经来回copy了好多次了，太顽固，就别再费事了
   2. 老年代满了FGC Full GC

eden : suvivor0 : suvivor1 = 8 : 1 : 1
new : old = 1 : 2 (Java 1.8)
这个比例的底层原因是：确信eden去里面绝大多数的对象会被一次GC回收掉
   
5. GC Tuning (Generation)
   1. 尽量减少FGC（调优的目标）, 否则会Stop the world。多么低算低要看业务场景。STW在自动回收的技术里是不可避免的
   2. MinorGC = YGC
   3. MajorGC = FGC
   
6. 对象分配过程图
   ![](对象分配过程详解.png)

7. 动态年龄：（不重要）
   https://www.jianshu.com/p/989d3b06a49d

8. 分配担保：（不重要）
   YGC期间 survivor区空间不够了 空间担保直接进入老年代
   参考：https://cloud.tencent.com/developer/article/1082730

#### 5.常见的垃圾回收器

![常用垃圾回收器](常用垃圾回收器.png)

1. JDK诞生 Serial追随 提高效率，诞生了PS，为了配合CMS，诞生了PN，CMS是1.4版本后期引入，CMS是里程碑式的GC，它开启了并发回收的过程，但是CMS毛病较多，因此目前任何一个JDK版本默认是CMS
   并发垃圾回收是因为无法忍受STW.所谓的Serial指的是单线程，parallel指的是多线程
2. Serial 年轻代 串行回收 a stop-the-world(停下所有的用户线程，垃圾回收线程上场，回收完了之后，程序继续运行), copying collector which uses a single GC thread
3. Parellel Scavenge 年轻代 多线程并行回收
4. ParNew（PN）， 就是Parellel Scavenge的新版本（目前还没有不会产生STW的垃圾回收器，ZGC能达到2ms的STW），年轻代 也是并行回收，
   为了配合CMS的并行回收而设计的
5. SerialOld 
6. ParallelOld 所谓的调优绝大多数都是跳的2、3、5、6，因为1.8默认的GC是PS + ParallelOld (-XX:+UseParallelGC) 重点关注这一种
7. ConcurrentMarkSweep CMS 针对老年代（FGC的时候） 并发的，老年代装不下了会触发CMS。垃圾回收和应用程序同时运行，降低STW的时间(200ms)，其他的GC可能需要几个
   小时才能弄完。CMS问题比较多，所以现在没有一个版本默认是CMS，只能手工指定。Concurrent的意思是正常程序和GC可以并发运行。  
   1. 初始标记： 标识最根上的对象，比如线程栈变量、静态变量、常量池、JNI指针，需要STW，但是时间比较短，根上的的垃圾比较少（好像是单线程）
   2. 并发标记： 80%GC时间都是浪费在这里的，现在它跟工作线程一起进行，不STW，客户可能感觉慢了点，但至少程序还有反应
   3. 重新标记： 并发标记标记的不全，在其间会产生新的垃圾，或者原来是垃圾的又变成不是垃圾了，所以要再来一遍，会有STW，但是很短暂（好像是多线程）
   4. 并发清理： GC线程和工作线程同时进行。期间也会产生新垃圾，就是浮动垃圾，等下一次GC清理掉
      
   *CMS的问题：*CMS既然是MarkSweep，就一定会有碎片化的问题，碎片到达一定程度，CMS的老年代分配对象分配不下的时候，使用SerialOld
   进行老年代回收，标记压缩. 拿CMS处理32G以上的大内存的时候基本上都会出问题。内存日志里出现Concurrent Mode Failure或者
   PromotionFailed的时候基本上就是因为内存碎片太多了。解决方式是降低-XX:CMSInitiatingOccupancyFraction,从92%降低到68%，让老年去有
   足够的预留空间，不至于请出SerialOld
   想象一下：
   PS + PO -> 加内存 换垃圾回收器 -> PN + CMS + SerialOld（几个小时 - 几天的STW，这与CMS的设计初衷相悖）
   几十个G的内存，单线程回收 -> G1 + FGC 几十个G -> 上T内存的服务器 ZGC
   算法：三色标记 + Incremental Update
8. G1(10ms，1.7之后，1.8成熟) 一般应用程序在200ms内都会有响应，如果追求的是Throughput，用Parallel更好。G1回收先*并发*收集垃圾最多的
   region的垃圾，在"并发"这个特点上来说，跟CMS没有太大的区别（分而治之和分层是软件工程的重要思想）。算法：三色标记 + SATB
   三色标记算法：有很多region，物理上在一起，但是逻辑上有不同的角色：Eden、Survivor、Old、Humongous（大对象，跨region）。三色标记是：
   把对象分成三个不同的颜色，每个颜色标志着他到底有没有被标记过，标记了一半了，还是完全没有被标记过。G1的应用场景是：不要求太高的吞吐量，
   但是要求很短的响应时间。想保证吞吐量就升级硬件去。
   
9. ZGC (1ms) PK C++， zero stw
   算法：ColoredPointers + LoadBarrier ColoredPointers：64位（无压缩）指针中有三个bit标识这个指针的指向有没有变化过，垃圾回收的时候
   会扫描变化过的指针。
10. Shenandoah
    算法：ColoredPointers + WriteBarrier
11. Eplison
12. PS 和 PN区别的延伸阅读：
    ▪[https://docs.oracle.com/en/java/javase/13/gctuning/ergonomics.html#GUID-3D0BB91E-9BFF-4EBB-B523-14493A860E73]
    (https://docs.oracle.com/en/java/javase/13/gctuning/ergonomics.html)
13. 垃圾收集器跟内存大小的关系
    1. Serial 几十兆
    2. PS 上百兆 - 几个G
    3. CMS - 20G
    4. G1 - 上百G
    5. ZGC - 4T - 16T（JDK13）

1.8默认的垃圾回收：PS + ParallelOld

非并发GC是先抛弃工作线程，然后垃圾足够多了，都停下，GC线程出场，回收垃圾，完事儿之后在开始工作线程。GC线程如果是单线程，就是SerialXXX，
如果是多线程，就是ParallelXX。而并发回收是指工作线程进行的同时就可以回收垃圾。发展的线索时内存从小变大的过程。G1以前的垃圾回收器都要把内存
分好，任何操作都要扫描整个代，当内存太大的时候就无法避免长时间低效了，这时候就有了G1，他把内存分为好多小块，工作线程在一块里面玩，GC回收另外
一小块，分而治之。ZGC的分块有更加灵活，有大有小。

### 常见垃圾回收器组合参数设定：(1.8)

* -XX:+UseSerialGC = Serial New (DefNew) + Serial Old
  * 小型程序。默认情况下不会是这种选项，HotSpot会根据计算及配置和JDK版本自动选择收集器
* -XX:+UseParNewGC = ParNew + SerialOld
  * 这个组合已经很少用（在某些版本中已经废弃）
  * https://stackoverflow.com/questions/34962257/why-remove-support-for-parnewserialold-anddefnewcms-in-the-future
* -XX:+UseConc<font color=red>(urrent)</font>MarkSweepGC = ParNew + CMS + Serial Old
* -XX:+UseParallelGC = Parallel Scavenge + Parallel Old (1.8默认) 【PS + SerialOld】
* -XX:+UseParallelOldGC = Parallel Scavenge + Parallel Old
* -XX:+UseG1GC = G1
* Linux中没找到默认GC的查看方法，而windows中会打印UseParallelGC 
  * java +XX:+PrintCommandLineFlags -version
  * 通过GC的日志来分辨

* Linux下1.8版本默认的垃圾回收器到底是什么？

  * 1.8.0_181 默认（看不出来）Copy MarkCompact
  * 1.8.0_222 默认 PS + PO

### JVM调优第一步，了解JVM常用命令行参数

* JVM的命令行参数参考：https://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html

* HotSpot参数分类

  > 标准： - 开头，所有的HotSpot都支持
  >
  > 非标准：-X 开头，特定版本HotSpot支持特定命令
  >
  > 不稳定：-XX 开头，下个版本可能取消

  java -version

  java -X

  

  试验用程序：

  ```java
  import java.util.List;
  import java.util.LinkedList;
  
  public class HelloGC {
    public static void main(String[] args) {
      System.out.println("HelloGC!");
      List list = new LinkedList();
      for(;;) {
        byte[] b = new byte[1024*1024];
        list.add(b);
      }
    }
  }
  ```



  1. 区分概念：内存泄漏memory leak，内存溢出out of memory
  2. java -XX:+PrintCommandLineFlags HelloGC
  3. java -Xmn10M -Xms40M -Xmx60M -XX:+PrintCommandLineFlags -XX:+PrintGC  HelloGC
     PrintGCDetails PrintGCTimeStamps PrintGCCauses。 一般-Xms40M -Xmx60M 这两个地方设置的值是一样的，不让堆有弹性，因为那样会来
     回扩大缩小浪费计算资源。-Xmn10M是指新生代的大小
  4. java -XX:+UseConcMarkSweepGC 
  5. java -XX:+PrintCommandLineFlags HelloGC (拿到运行时传进去的那些启动的时候的命令行参数，无害，只是观察)
  6. java -XX:+UseG1GC
  7. java -XX:+PrintFlagsInitial 默认参数值
  8. java -XX:+PrintFlagsFinal 最终参数值
  9. java -XX:+PrintFlagsFinal | grep xxx 找到对应的参数
  10. java -XX:+PrintFlagsFinal -version |grep GC

### PS GC日志详解

每种垃圾回收器的日志格式是不同的！

PS日志格式

![GC日志详解](./GC日志详解.png)
Times： user，sys，real分别代表用户态、内核态个小号了多少时间，以及总时间
heap dump部分：

```java
eden space 5632K, 94% used [0x00000000ff980000,0x00000000ffeb3e28,0x00000000fff00000)
                            后面的内存地址指的是，起始地址，使用空间结束地址，整体空间结束地址
```

![GCHeapDump](GCHeapDump.png)

total = eden + 1个survivor

没有必要看JVM源码，装一下用的

### 调优前的基础概念：

1. 吞吐量：用户代码时间 /（用户代码执行时间 + 垃圾回收时间）。吞吐量越大，说明干正经事儿的时间也越多
2. 响应时间：STW越短，响应时间越好
CMS带来的是STW缩短，但是吞吐量下降了，调优看你追求哪方面。科学计算、数据挖掘：注重吞吐量，PS+PO。网站、API调用就是响应时间优先：G1，
或者PN+CMS

所谓调优，首先确定，追求啥？吞吐量优先，还是响应时间优先？还是在满足一定的响应时间的情况下，要求达到多大的吞吐量...

问题：

科学计算，吞吐量。数据挖掘，thrput。吞吐量优先的一般：（PS + PO）

响应时间：网站 GUI API （1.8 G1）

### 什么是调优？

1. 根据需求进行JVM规划和预调优
2. 优化运行JVM运行环境（慢，卡顿）
3. 解决JVM运行过程中出现的各种问题(OOM)
PS：重启有时候很直接很管用。TB历年来最高的并发：54W tps。100w并发就太高了，垂直电商数百就很不错了。调优一般是先拿一台机器看看它能支持多少，
然后根据这个数字扩展

### 调优，从规划开始

* 调优，从业务场景开始，没有业务场景的调优都是耍流氓
  
* 无监控（压力测试，能看到结果），不调优。
调整业务有时候效果更明显    

* 步骤：
  1. 熟悉业务场景（没有最好的垃圾回收器，只有最合适的垃圾回收器）
     1. 响应时间、停顿时间 [CMS G1 ZGC] （需要给用户作响应）
     2. 吞吐量 = 用户时间 /( 用户时间 + GC时间) [PS]
  2. 选择回收器组合
  3. 计算内存需求（经验值 1.5G 16G）
  4. 选定CPU（越高越好）
  5. 设定年代大小、升级年龄
  6. 设定日志参数
     1. -Xloggc:/opt/xxx/logs/xxx-xxx-gc-%t.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=20M -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCCause
     每个日志文件20M，一共5个，滚动记录，满了就干掉最前面那一个，总共最大就100M
     2. 或者每天产生一个日志文件
  7. 观察日志情况
  
* 案例1：垂直电商，最高每日百万订单，处理订单系统需要什么样的服务器配置？

  > 这个问题比较业余，因为很多不同的服务器配置都能支撑(1.5G 16G)
  > 找最高峰的几个小时
  > 1小时360000集中时间段， 100个订单/秒，（找一小时内的高峰期，1000订单/秒）内存的选择就根据这个100或1000来
  >
  > 经验值，压测，实在不行加CPU和内存
  >
  > 非要计算：一个订单产生需要多少内存？512K * 1000 500M内存
  >
  > 专业一点儿问法：要求响应时间100ms
  >
  > 压测！

* 案例2：12306遭遇春节大规模抢票应该如何支撑？

  > 12306应该是中国并发量最大的秒杀网站：
  >
  > 号称并发量100W最高
  >
  > CDN -> LVS -> NGINX -> 业务系统 -> 每台机器1W并发（10K问题，Redis可解决） 100台机器
  >
  > 普通电商订单 -> 下单 ->订单系统（IO）减库存 ->等待用户付款
  >
  > 12306的一种可能的模型： 下单 -> 减库存 和 订单(redis kafka) 同时异步进行 ->等付款
  > 先开两个线程，一个减库存（比较简单），另一个付款，就把订单扔到Kafka或者Redis里，然后给用户返回下单成功的信息，就等您付款了。
  > 什么时候付款完成，什么时候订单处理线程就去Kafka或者Redis里面拿数据，把他持久化到HBase或者MySQL
  > 
  >
  > 如果Transaction必须得等减完库存然后再付款，tps撑不了多少
  > 减库存最后还会把压力压到一台服务器
  >
  > 可以做分布式本地库存 + 单独服务器做库存均衡
  > 就是说可以每一条列车线有一台服务器负责减库存（要我设计就用Redis），但是会有数据倾斜，有的列车票卖得很快或者访问量很多，
  > 所以要再来个LB机器，看着数据或者业务分配不均匀了，就调整一部分过去
  >
  > 大流量的处理方法：分而治之，👍

* 怎么得到一个事务会消耗多少内存？

  > 1. 弄台机器，看能承受多少TPS？是不是达到目标？扩容或调优，让它达到
  >
  > 2. 用压测来确定

### 优化环境

1. 有一个50万PV的资料类网站（从磁盘提取文档到内存）原服务器32位，1.5G
   的堆，用户反馈网站比较缓慢，因此公司决定升级，新的服务器为64位，16G
   的堆内存，结果用户反馈卡顿十分严重，反而比以前效率更低了
   1. 为什么原网站慢?
      很多用户浏览数据，很多数据load到内存，内存不足，频繁GC，STW长，响应时间变慢
   2. 为什么会更卡顿？
      内存越大，FGC时间越长
   3. 咋办？
      PS -> PN + CMS 或者 G1 （提高CPU性能也行吧）
2. 系统CPU经常100%，如何调优？(面试高频)
   CPU100%那么一定有线程在占用系统资源，
   1. 找出哪个进程cpu高（top）
   2. 该进程中的哪个线程cpu高（top -Hp）
   3. 导出该线程的堆栈 (jstack)
   4. 查找哪个方法（栈帧）消耗时间 (jstack)
   5. 工作线程占比高 | 垃圾回收线程占比高
3. 系统内存飙高，如何查找问题？（面试高频）
    CPU飙高和内存标高本质上是两类问题
   1. 导出堆内存 (jmap)
   2. 分析 (jhat jvisualvm mat jprofiler ... )
4. 如何监控JVM
   1. jstat jvisualvm jprofiler arthas top...

### 解决JVM运行中的问题

#### 一个案例理解常用工具

1. 测试代码：

   ```java
   package com.mashibing.jvm.gc;
   
   import java.math.BigDecimal;
   import java.util.ArrayList;
   import java.util.Date;
   import java.util.List;
   import java.util.concurrent.ScheduledThreadPoolExecutor;
   import java.util.concurrent.ThreadPoolExecutor;
   import java.util.concurrent.TimeUnit;
   
   /**
    * 从数据库中读取信用数据，套用模型，并把结果进行记录和传输
    */
   
   public class T15_FullGC_Problem01 {
   
       private static class CardInfo {
           BigDecimal price = new BigDecimal(0.0);
           String name = "张三";
           int age = 5;
           Date birthdate = new Date();
   
           public void m() {}
       }
   
       private static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(50,
               new ThreadPoolExecutor.DiscardOldestPolicy());
   
       public static void main(String[] args) throws Exception {
           executor.setMaximumPoolSize(50);
   
           for (;;){
               modelFit();
               Thread.sleep(100);
           }
       }
   
       private static void modelFit(){
           List<CardInfo> taskList = getAllCardInfo();
           taskList.forEach(info -> {
               // do something
               executor.scheduleWithFixedDelay(() -> {
                   //do sth with info
                   info.m();
   
               }, 2, 3, TimeUnit.SECONDS);
           });
       }
   
       private static List<CardInfo> getAllCardInfo(){
           List<CardInfo> taskList = new ArrayList<>();
   
           for (int i = 0; i < 100; i++) {
               CardInfo ci = new CardInfo();
               taskList.add(ci);
           }
   
           return taskList;
       }
   }
   
   ```

2. java -Xms200M -Xmx200M -XX:+PrintGC com.mashibing.jvm.gc.T15_FullGC_Problem01

3. 一般是运维团队首先受到报警信息（CPU Memory）
   网管会用网管软件如Ansible，每台机器如果CPU、内存级别有问题就会报警

4. top命令观察到问题：内存不断增长 CPU占用率居高不下，查看是哪个进程造成的

5. top -Hp 进程号 观察进程中的线程，哪个线程CPU和内存占比高

6. jps定位具体java进程
   jstack 定位线程状况，重点关注：WAITING BLOCKED
   eg. WAITING (on object monitor)
   waiting on <0x0000000088ca3310> (a java.lang.Object)
   假如有一个进程中100个线程，很多线程都在waiting on <xx> ，一定要找到是哪个线程持有这把锁
   怎么找？搜索jstack dump的信息，找<0x0000000088ca3310> ，看哪个线程持有这把锁RUNNABLE
   作业：1：写一个死锁程序，用jstack观察 2 ：写一个程序，一个线程持有锁不释放，其他线程等待

7. 为什么阿里规范里规定，线程的名称（尤其是线程池）都要写有意义的名称? 出了问题好定位
   怎么样自定义线程池里的线程名称？（自定义ThreadFactory）

8. jinfo pid 

9. jstat -gc 动态观察gc情况 / 阅读GC日志发现频繁GC / arthas观察 / jconsole/jvisualVM/ Jprofiler（最好用）
   jstat -gc 4655 500 : 每个500个毫秒打印GC的情况
   如果面试官问你是怎么定位OOM问题的？如果你回答用图形界面（错误）
   1：已经上线的系统不用图形界面用什么？（cmdline arthas）
   2：图形界面到底用在什么地方？测试！测试的时候进行监控！（压测观察）

10. jmap - histo 4655 | head -20，查找有多少对象产生

11. jmap -dump:format=b,file=xxx pid ：（在线dump转储文件）

    线上系统，内存特别大，jmap（命令11）执行期间会对进程产生很大影响，甚至卡顿（电商不适合）咋办？
    1：设定了参数HeapDump（HeapDumpOnOutOfMemoryError），OOM的时候会自动产生堆转储文件
    2：<font color='red'>很多服务器备份（高可用），停掉这台服务器对其他服务器不影响</font> 这种比较通用吧，隔离之后再用jmap导出
    3：在线定位(arthas，一般小点儿公司用不到)
    数据库连接没释放掉也可能引起问题（连接数据库超时），这要看数据库连接池的日志，与JVM内存没有太大关系，这会儿早就抛异常了。  
    还有很多问题是由第三方类库产生的，改起来费劲

12. java -Xms20M -Xmx20M -XX:+UseParallelGC -XX:+HeapDumpOnOutOfMemoryError com.mashibing.jvm.gc.T15_FullGC_Problem01

13. 使用MAT / jhat /jvisualvm 进行dump文件分析
     https://www.cnblogs.com/baihuitestsoftware/articles/6406271.html 
jhat -J-mx512M xxx.dump
    http://192.168.17.11:7000
    拉到最后：找到对应链接
    可以使用OQL查找特定问题对象
    
14. 找到代码的问题，过程是这样：1.运维团队上报问题 2.top/jps查看那个进程CPU利用率高 3.jstack看看是不是思索，或者那个线程有问题
    4.如果是频繁GC，如果飚高的是垃圾回收线程，jmap看哪个对象instance和占用内存多

#### jconsole远程连接

1. 程序启动加入参数：

   > ```shell
   > java -Djava.rmi.server.hostname=192.168.17.11 -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=11111 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false XXX
   > ```

这里要打开JMX，远程监控，java有个标准的访问远程服务的协议叫JMX，java要开启服务和端口让远程能访问到才可以。有些生产环境的Tomcat是打开的。
但是JMX打开的话，对于服务器的性能还是挺有影响的，所以一般不会这么干。

2. 如果遭遇 Local host name unknown：XXX的错误，修改/etc/hosts文件，把XXX加入进去

   > ```java
   > 192.168.17.11 basic localhost localhost.localdomain localhost4 localhost4.localdomain4
   > ::1         localhost localhost.localdomain localhost6 localhost6.localdomain6
   > ```

3. 关闭linux防火墙（实战中应该打开对应端口）

   > ```shell
   > service iptables stop
   > chkconfig iptables off #永久关闭
   > ```

4. windows上打开 jconsole远程连接 192.168.17.11:11111

#### jvisualvm远程连接

 https://www.cnblogs.com/liugh/p/7620336.html （简单做法）
 登录host、选择JVM、选择抽样器(Sampler),点击 "Memory"

#### jprofiler (收费)

#### arthas在线排查工具

* 为什么需要在线排查？
   在生产上我们经常会碰到一些不好排查的问题，例如线程安全问题，用最简单的threaddump或者heapdump不好查到问题原因。为了排查这些问题，有时我们会临时加一些日志，比如在一些关键的函数里打印出入参，然后重新打包发布，如果打了日志还是没找到问题，继续加日志，重新打包发布。对于上线流程复杂而且审核比较严的公司，从改代码到上线需要层层的流转，会大大影响问题排查的进度。 
* arthas命令行下输入：jvm观察jvm信息
* thread定位线程问题
* dashboard 观察系统情况
* heapdump + jhat分析: 
  直接输入heapdump，或者`heapdump 文件名` 会对主进程有影响 `jhat -J-mx512M XXX.hprof`, 指定个参数，一点点地导
   最后可以用浏览器通过7000端口访问,期望也最下端可以看histogram和执行OQL （`select s from java.lang.String s where s.value.length >= 100`)
* jad反编译
   动态代理生成类的问题定位
   第三方的类（观察代码）
   版本问题（确定自己最新提交的版本是不是被使用）
* redefine 热替换
   目前有些限制条件：只能改方法实现（方法已经运行完成），不能改方法名， 不能改属性. 用了ClassLoader里面的redefine方法
   m() -> mm()现在还不行
* sc  - search class
* watch  - watch method
* 没有包含的功能：jmap

### GC算法的基础概念

* Card Table
  由于做YGC时，需要扫描整个OLD区，效率非常低，所以JVM设计了CardTable， 如果一个OLD区CardTable中有对象指向Y区，就将它设为Dirty，下次扫描时，只需要扫描Dirty Card
  在结构上，Card Table用BitMap来实现

### CMS

#### CMS的问题

1. Memory Fragmentation

   > -XX:+UseCMSCompactAtFullCollection
   > -XX:CMSFullGCsBeforeCompaction 默认为0 指的是经过多少次FGC才进行压缩

2. Floating Garbage

   > Concurrent Mode Failure
   > 产生：if the concurrent collector is unable to finish reclaiming the unreachable objects before the tenured generation fills up, or if an allocation cannot be satisfiedwith the available free space blocks in the tenured generation, then theapplication is paused and the collection is completed with all the applicationthreads stopped
   >
   > 解决方案：降低触发CMS的阈值
   >
   > PromotionFailed
   >
   > 解决方案类似，保持老年代有足够的空间
   >
   > –XX:CMSInitiatingOccupancyFraction 92% 可以降低这个值，让CMS保持老年代足够的空间

#### CMS日志分析

执行命令：java -Xms20M -Xmx20M -XX:+PrintGCDetails -XX:+UseConcMarkSweepGC com.mashibing.jvm.gc.T15_FullGC_Problem01

[GC (Allocation Failure) [ParNew: 6144K->640K(6144K), 0.0265885 secs] 6585K->2770K(19840K), 0.0268035 secs] [Times: user=0.02 sys=0.00, real=0.02 secs] 

> ParNew：年轻代收集器
>
> 6144->640：收集前后的对比
>
> （6144）：整个年轻代容量
>
> 6585 -> 2770：整个堆的情况
>
> （19840）：整个堆大小



```java
[GC (CMS Initial Mark) [1 CMS-initial-mark: 8511K(13696K)] 9866K(19840K), 0.0040321 secs] [Times: user=0.01 sys=0.00, real=0.00 secs] 
	//8511 (13696) : 老年代使用（最大）
	//9866 (19840) : 整个堆使用（最大）
[CMS-concurrent-mark-start]
[CMS-concurrent-mark: 0.018/0.018 secs] [Times: user=0.01 sys=0.00, real=0.02 secs] 
	//这里的时间意义不大，因为是并发执行
[CMS-concurrent-preclean-start]
[CMS-concurrent-preclean: 0.000/0.000 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
	//标记Card为Dirty，也称为Card Marking
[GC (CMS Final Remark) [YG occupancy: 1597 K (6144 K)][Rescan (parallel) , 0.0008396 secs][weak refs processing, 0.0000138 secs][class unloading, 0.0005404 secs][scrub symbol table, 0.0006169 secs][scrub string table, 0.0004903 secs][1 CMS-remark: 8511K(13696K)] 10108K(19840K), 0.0039567 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
	//STW阶段，YG occupancy:年轻代占用及容量
	//[Rescan (parallel)：STW下的存活对象标记
	//weak refs processing: 弱引用处理
	//class unloading: 卸载用不到的class
	//scrub symbol(string) table: 
		//cleaning up symbol and string tables which hold class-level metadata and 
		//internalized string respectively
	//CMS-remark: 8511K(13696K): 阶段过后的老年代占用及容量
	//10108K(19840K): 阶段过后的堆占用及容量

[CMS-concurrent-sweep-start]
[CMS-concurrent-sweep: 0.005/0.005 secs] [Times: user=0.00 sys=0.00, real=0.01 secs] 
	//标记已经完成，进行并发清理
[CMS-concurrent-reset-start]
[CMS-concurrent-reset: 0.000/0.000 secs] [Times: user=0.00 sys=0.00, real=0.00 secs]
	//重置内部结构，为下次GC做准备
```



### G1

1. ▪https://www.oracle.com/technical-resources/articles/java/g1gc.html

#### G1日志详解

```java
[GC pause (G1 Evacuation Pause) (young) (initial-mark), 0.0015790 secs]
//young -> 年轻代 Evacuation-> 复制存活对象 
//initial-mark 混合回收的阶段，这里是YGC混合老年代回收
   [Parallel Time: 1.5 ms, GC Workers: 1] //一个GC线程
      [GC Worker Start (ms):  92635.7]
      [Ext Root Scanning (ms):  1.1]
      [Update RS (ms):  0.0]
         [Processed Buffers:  1]
      [Scan RS (ms):  0.0]
      [Code Root Scanning (ms):  0.0]
      [Object Copy (ms):  0.1]
      [Termination (ms):  0.0]
         [Termination Attempts:  1]
      [GC Worker Other (ms):  0.0]
      [GC Worker Total (ms):  1.2]
      [GC Worker End (ms):  92636.9]
   [Code Root Fixup: 0.0 ms]
   [Code Root Purge: 0.0 ms]
   [Clear CT: 0.0 ms]
   [Other: 0.1 ms]
      [Choose CSet: 0.0 ms]
      [Ref Proc: 0.0 ms]
      [Ref Enq: 0.0 ms]
      [Redirty Cards: 0.0 ms]
      [Humongous Register: 0.0 ms]
      [Humongous Reclaim: 0.0 ms]
      [Free CSet: 0.0 ms]
   [Eden: 0.0B(1024.0K)->0.0B(1024.0K) Survivors: 0.0B->0.0B Heap: 18.8M(20.0M)->18.8M(20.0M)]
 [Times: user=0.00 sys=0.00, real=0.00 secs] 
//以下是混合回收其他阶段
[GC concurrent-root-region-scan-start]
[GC concurrent-root-region-scan-end, 0.0000078 secs]
[GC concurrent-mark-start]
//无法evacuation，进行FGC
[Full GC (Allocation Failure)  18M->18M(20M), 0.0719656 secs]
   [Eden: 0.0B(1024.0K)->0.0B(1024.0K) Survivors: 0.0B->0.0B Heap: 18.8M(20.0M)->18.8M(20.0M)], [Metaspace: 38
76K->3876K(1056768K)] [Times: user=0.07 sys=0.00, real=0.07 secs]

```



### 案例汇总

OOM产生的原因多种多样，有些程序未必产生OOM，不断FGC(CPU飙高，但内存回收特别少) （上面案例）

1. 硬件升级系统反而卡顿的问题（见上）  
   内存变大了，原来的GC做FGC的时候时间变长了，积重难返。不是说内存变大之后系统的性能就一定能够提升，还要选用特定的垃圾回收器才可以。  
   PN+CMS或者G1来替代PS+PO就可以了

2. 线程池不当运用产生OOM问题（见上）
   不断的往List里加对象（实在太LOW）

3. smile jira问题
   实际系统不断重启
   解决问题 加内存 + 更换垃圾回收器 G1
   真正问题在哪儿？不知道

4. tomcat http-header-size过大问题（Hector）

5. lambda表达式导致方法区溢出问题(MethodArea / Perm Metaspace)
   对于lambda表达式 `I i = C::n;` 每一个 i 都会产生一个 Class，而Class对象被分配在元空间，如果不断循环执行这一句，则会方法区溢出  
   LambdaGC.java     -XX:MaxMetaspaceSize=9M -XX:+PrintGCDetails
   一旦出现 `Compressed class space` 就说明方法区溢出了, 方法区在方法没结束的时候不会被清理（有的GC不清理，有的清理条件比较苛刻），
   实际很少发生，Class没有对应的对象了Class可能会被回收。JVM各个部分都会有溢出
   

   ```java
   "C:\Program Files\Java\jdk1.8.0_181\bin\java.exe" -XX:MaxMetaspaceSize=9M -XX:+PrintGCDetails "-javaagent:C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2019.1\lib\idea_rt.jar=49316:C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2019.1\bin" -Dfile.encoding=UTF-8 -classpath "C:\Program Files\Java\jdk1.8.0_181\jre\lib\charsets.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\deploy.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\access-bridge-64.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\cldrdata.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\dnsns.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\jaccess.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\jfxrt.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\localedata.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\nashorn.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\sunec.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\sunjce_provider.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\sunmscapi.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\sunpkcs11.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\ext\zipfs.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\javaws.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\jce.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\jfr.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\jfxswt.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\jsse.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\management-agent.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\plugin.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\resources.jar;C:\Program Files\Java\jdk1.8.0_181\jre\lib\rt.jar;C:\work\ijprojects\JVM\out\production\JVM;C:\work\ijprojects\ObjectSize\out\artifacts\ObjectSize_jar\ObjectSize.jar" com.mashibing.jvm.gc.LambdaGC
   [GC (Metadata GC Threshold) [PSYoungGen: 11341K->1880K(38400K)] 11341K->1888K(125952K), 0.0022190 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
   [Full GC (Metadata GC Threshold) [PSYoungGen: 1880K->0K(38400K)] [ParOldGen: 8K->1777K(35328K)] 1888K->1777K(73728K), [Metaspace: 8164K->8164K(1056768K)], 0.0100681 secs] [Times: user=0.02 sys=0.00, real=0.01 secs] 
   [GC (Last ditch collection) [PSYoungGen: 0K->0K(38400K)] 1777K->1777K(73728K), 0.0005698 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
   [Full GC (Last ditch collection) [PSYoungGen: 0K->0K(38400K)] [ParOldGen: 1777K->1629K(67584K)] 1777K->1629K(105984K), [Metaspace: 8164K->8156K(1056768K)], 0.0124299 secs] [Times: user=0.06 sys=0.00, real=0.01 secs] 
   java.lang.reflect.InvocationTargetException
   	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   	at java.lang.reflect.Method.invoke(Method.java:498)
   	at sun.instrument.InstrumentationImpl.loadClassAndStartAgent(InstrumentationImpl.java:388)
   	at sun.instrument.InstrumentationImpl.loadClassAndCallAgentmain(InstrumentationImpl.java:411)
   Caused by: java.lang.OutOfMemoryError: Compressed class space
   	at sun.misc.Unsafe.defineClass(Native Method)
   	at sun.reflect.ClassDefiner.defineClass(ClassDefiner.java:63)
   	at sun.reflect.MethodAccessorGenerator$1.run(MethodAccessorGenerator.java:399)
   	at sun.reflect.MethodAccessorGenerator$1.run(MethodAccessorGenerator.java:394)
   	at java.security.AccessController.doPrivileged(Native Method)
   	at sun.reflect.MethodAccessorGenerator.generate(MethodAccessorGenerator.java:393)
   	at sun.reflect.MethodAccessorGenerator.generateSerializationConstructor(MethodAccessorGenerator.java:112)
   	at sun.reflect.ReflectionFactory.generateConstructor(ReflectionFactory.java:398)
   	at sun.reflect.ReflectionFactory.newConstructorForSerialization(ReflectionFactory.java:360)
   	at java.io.ObjectStreamClass.getSerializableConstructor(ObjectStreamClass.java:1574)
   	at java.io.ObjectStreamClass.access$1500(ObjectStreamClass.java:79)
   	at java.io.ObjectStreamClass$3.run(ObjectStreamClass.java:519)
   	at java.io.ObjectStreamClass$3.run(ObjectStreamClass.java:494)
   	at java.security.AccessController.doPrivileged(Native Method)
   	at java.io.ObjectStreamClass.<init>(ObjectStreamClass.java:494)
   	at java.io.ObjectStreamClass.lookup(ObjectStreamClass.java:391)
   	at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1134)
   	at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
   	at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1509)
   	at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
   	at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
   	at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
   	at javax.management.remote.rmi.RMIConnectorServer.encodeJRMPStub(RMIConnectorServer.java:727)
   	at javax.management.remote.rmi.RMIConnectorServer.encodeStub(RMIConnectorServer.java:719)
   	at javax.management.remote.rmi.RMIConnectorServer.encodeStubInAddress(RMIConnectorServer.java:690)
   	at javax.management.remote.rmi.RMIConnectorServer.start(RMIConnectorServer.java:439)
   	at sun.management.jmxremote.ConnectorBootstrap.startLocalConnectorServer(ConnectorBootstrap.java:550)
   	at sun.management.Agent.startLocalManagementAgent(Agent.java:137)
   
   ```

6. 直接内存溢出问题（少见）
   《深入理解Java虚拟机》P59，使用Unsafe分配直接内存，或者使用NIO的问题

7. 栈溢出问题
   -Xss设定太小、无限递归。一个方法在被调用的时候就会产生一个栈帧，在没有退出的情况下再被调用，又会有一个栈帧

8. 比较一下这两段程序的异同，分析哪一个是更优的写法：

   ```java 
   Object o = null;
   for(int i=0; i<100; i++) {
       o = new Object();
       //业务处理
   }
   ```

   ```java
   for(int i=0; i<100; i++) {
       Object o = new Object();
   }
   ```
   当然是第一种写法，第二种每进行一次循环就产生一个o，循环不结束会一直有对象产生不会被回收，并且栈上还有个引用指向他

9. 重写finalize引发频繁GC
   小米云，HBase同步系统，系统通过nginx访问超时报警（是由于CPU飙高），最后排查，C++程序员重写finalize引发频繁GC问题
为什么C++程序员会重写finalize？（new delete）因为C++要手动回收，所以C++程序员在写类的时候顺手写了finalize，把它当成析构函数用了。
finalize耗时比较长（200ms），好多对象积压，YGC回收不过来就会频繁FGC
   
   
10. 如果有一个系统，内存一直消耗不超过10%，但是观察GC日志，发现FGC总是频繁产生，会是什么引起的？
    有人显式调用了 `System.gc()` (这个比较Low)

11. Distuptor有个可以设置链的长度，如果过大，然后对象大，消费完不主动释放，会溢出 (来自 死物风情)

12. 用jvm都会溢出，mycat用崩过，1.6.5某个临时版本解析sql子查询算法有问题，9个exists的联合sql就导致生成几百万的对象（来自 死物风情）

13. new 大量线程，会产生 native thread OOM，（low）应该用线程池，
    解决方案：减少堆空间（太TMlow了）,预留更多内存产生native thread
    JVM内存占物理内存比例 50% - 80%，差不多20%给native thread


### GC常用参数

* -Xmn -Xms -Xmx -Xss
  年轻代 最小堆 最大堆 栈空间
* -XX:+UseTLAB
  使用TLAB，默认打开
* -XX:+PrintTLAB
  打印TLAB的使用情况
* -XX:TLABSize
  设置TLAB大小
* -XX:+DisableExplictGC
  System.gc()不管用 ，FGC
* -XX:+PrintGC
* -XX:+PrintGCDetails
* -XX:+PrintHeapAtGC
* -XX:+PrintGCTimeStamps
* -XX:+PrintGCApplicationConcurrentTime (低)
  打印应用程序时间
* -XX:+PrintGCApplicationStoppedTime （低）
  打印暂停时长
* -XX:+PrintReferenceGC （重要性低）
  记录回收了多少种不同引用类型的引用
* -verbose:class
  类加载详细过程
* -XX:+PrintVMOptions
* -XX:+PrintFlagsFinal  -XX:+PrintFlagsInitial
  必须会用
* -Xloggc:opt/log/gc.log
* -XX:MaxTenuringThreshold
  升代年龄，最大值15
* 锁自旋次数 -XX:PreBlockSpin 热点代码检测参数-XX:CompileThreshold 逃逸分析 标量替换 ... 
  这些不建议设置

### Parallel常用参数

* -XX:SurvivorRatio
* -XX:PreTenureSizeThreshold
  大对象到底多大
* -XX:MaxTenuringThreshold
* -XX:+ParallelGCThreads
  并行收集器的线程数，同样适用于CMS，一般设为和CPU核数相同
* -XX:+UseAdaptiveSizePolicy
  自动选择各区大小比例

### CMS常用参数

* -XX:+UseConcMarkSweepGC
* -XX:ParallelCMSThreads
  CMS线程数量
* -XX:CMSInitiatingOccupancyFraction
  使用多少比例的老年代后开始CMS收集，默认是68%(近似值)，如果频繁发生SerialOld卡顿，应该调小，（频繁CMS回收）
* -XX:+UseCMSCompactAtFullCollection
  在FGC时进行压缩
* -XX:CMSFullGCsBeforeCompaction
  多少次FGC之后进行压缩
* -XX:+CMSClassUnloadingEnabled
* -XX:CMSInitiatingPermOccupancyFraction
  达到什么比例时进行Perm回收
* GCTimeRatio
  设置GC时间占用程序运行时间的百分比
* -XX:MaxGCPauseMillis
  停顿时间，是一个建议时间，GC会尝试用各种手段达到这个时间，比如减小年轻代

### G1常用参数

* -XX:+UseG1GC
* -XX:MaxGCPauseMillis
  建议值，G1会尝试调整Young区的块数来达到这个值
* -XX:GCPauseIntervalMillis
  ？GC的间隔时间
* -XX:+G1HeapRegionSize
  分区大小，建议逐渐增大该值，1 2 4 8 16 32。
  随着size增加，垃圾的存活时间更长，GC间隔更长，但每次GC的时间也会更长
  ZGC做了改进（动态区块大小）
* G1NewSizePercent
  新生代最小比例，默认为5%
* G1MaxNewSizePercent
  新生代最大比例，默认为60%
* GCTimeRatio
  GC时间建议比例，G1会根据这个值调整堆空间
* ConcGCThreads
  线程数量
* InitiatingHeapOccupancyPercent
  启动G1的堆空间占用比例



#### 作业

1. -XX:MaxTenuringThreshold控制的是什么？
   A: 对象升入老年代的年龄
     	B: 老年代触发FGC时的内存垃圾比例

2. 生产环境中，倾向于将最大堆内存和最小堆内存设置为：（为什么？）
   A: 相同 B：不同

3. JDK1.8默认的垃圾回收器是：
   A: ParNew + CMS
     	B: G1
     	C: PS + ParallelOld
     	D: 以上都不是

4. 什么是响应时间优先？

5. 什么是吞吐量优先？

6. ParNew和PS的区别是什么？

7. ParNew和ParallelOld的区别是什么？（年代不同，算法不同）

8. 长时间计算的场景应该选择：A：停顿时间 B: 吞吐量

9. 大规模电商网站应该选择：A：停顿时间 B: 吞吐量

10. HotSpot的垃圾收集器最常用有哪些？

11. 常见的HotSpot垃圾收集器组合有哪些？

12. JDK1.7 1.8 1.9的默认垃圾回收器是什么？如何查看？

13. 所谓调优，到底是在调什么？

14. 如果采用PS + ParrallelOld组合，怎么做才能让系统基本不产生FGC

15. 如果采用ParNew + CMS组合，怎样做才能够让系统基本不产生FGC

     1.加大JVM内存

     2.加大Young的比例

     3.提高Y-O的年龄

     4.提高S区比例

     5.避免代码内存泄漏

16. G1是否分代？G1垃圾回收器会产生FGC吗？

17. 如果G1产生FGC，你应该做什么？

      1. 扩内存
      2. 提高CPU性能（回收的快，业务逻辑产生对象的速度固定，垃圾回收越快，内存空间越大）
      3. 降低MixedGC触发的阈值，让MixedGC提早发生（默认是45%）

 18. 问：生产环境中能够随随便便的dump吗？
     小堆影响不大，大堆会有服务暂停或卡顿（加live可以缓解），dump前会有FGC

 19. 问：常见的OOM问题有哪些？
     栈 堆 MethodArea 直接内存



### 参考资料

1. [https://blogs.oracle.com/](https://blogs.oracle.com/jonthecollector/our-collectors)[jonthecollector](https://blogs.oracle.com/jonthecollector/our-collectors)[/our-collectors](https://blogs.oracle.com/jonthecollector/our-collectors)
2. https://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html
3. http://java.sun.com/javase/technologies/hotspot/vmoptions.jsp
4. JVM调优参考文档：https://docs.oracle.com/en/java/javase/13/gctuning/introduction-garbage-collection-tuning.html#GUID-8A443184-7E07-4B71-9777-4F12947C8184 
5. https://www.cnblogs.com/nxlhero/p/11660854.html 在线排查工具
6. https://www.jianshu.com/p/507f7e0cc3a3 arthas常用命令
7. Arthas手册：
   1. 启动arthas java -jar arthas-boot.jar
   2. 绑定java进程
   3. dashboard命令观察系统整体情况
   4. help 查看帮助
   5. help xx 查看具体命令帮助
8. jmap命令参考： https://www.jianshu.com/p/507f7e0cc3a3 
   1. jmap -heap pid
   2. jmap -histo pid
   3. jmap -clstats pid



