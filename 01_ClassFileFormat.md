# JVM

## 1：JVM基础知识

Java 是解释型和编译型语言的混合。有字节码解释器，也有JIT即时编译器。JIT负责编译频繁使用的代码，提升效率。就像C语言在Windows上执行的时候被编译成exe一样，
下次再执行的时候就快了，不需要解释器一句句解释了，执行引擎直接交给OS，效率高很多。不是所有的代码都需要JIT即时编译，否则Java就没法跨平台了。
https://docs.oracle.com/javase/specs/index.html

1. 什么是JVM
   跨语言的平台，有100多种语言可以在JVM上面跑。JVM帮助语言屏蔽了OS的底层实现
2. 常见的JVM
   Hotspot Jrockit J9 MicrosoftVM， TaobaoVM LiquidVM azul zing
3. JVM JRE JDK  
   JDK = JRE + development kit, JRE = JVM + core lib(内有String、Object类)

## 2：ClassFileFormat
任何语言，只要能够编译成（或者运行时变成class的二进制流）class文件，符合class的规范，他就可以在JVM上面运行，Java Kotlin，scala、Groovy等都可以。
所以从这个角度讲，JVM跟Java是没有关系的（略怪异），只跟class格式的文件有关系，跟各种语言没有任何关系JVM是一种规范，他定义了虚拟机应该执行什么等（各版本不一样）
虚构出来的一台计算机：1 字节码指令集（汇编语言）2 内存管理：堆、栈、方法区等  

javap命令查看：
```
Shuzheng-Mac2:JVM shuzheng$ javap -v /Users/shuzheng/IdeaProjects/JVM/out/production/JVM/com/mashibing/jvm/c1_bytecode/T0100_ByteCode01.class
Classfile /Users/shuzheng/IdeaProjects/JVM/out/production/JVM/com/mashibing/jvm/c1_bytecode/T0100_ByteCode01.class
  Last modified May 30, 2020; size 333 bytes
  SHA-256 checksum 641b18fa819eeb9958011a1cf2d7bd23a722aea5dcd38963787b121dd0541402
  Compiled from "T0100_ByteCode01.java"
public class com.mashibing.jvm.c1_bytecode.T0100_ByteCode01
  minor version: 0
  major version: 52
  flags: (0x0021) ACC_PUBLIC, ACC_SUPER
  this_class: #2                          // com/mashibing/jvm/c1_bytecode/T0100_ByteCode01
  super_class: #3                         // java/lang/Object
  interfaces: 0, fields: 0, methods: 1, attributes: 1
Constant pool:
   #1 = Methodref          #3.#13         // java/lang/Object."<init>":()V
   #2 = Class              #14            // com/mashibing/jvm/c1_bytecode/T0100_ByteCode01
   #3 = Class              #15            // java/lang/Object
   #4 = Utf8               <init>
   #5 = Utf8               ()V
   #6 = Utf8               Code
   #7 = Utf8               LineNumberTable
   #8 = Utf8               LocalVariableTable
   #9 = Utf8               this
  #10 = Utf8               Lcom/mashibing/jvm/c1_bytecode/T0100_ByteCode01;
  #11 = Utf8               SourceFile
  #12 = Utf8               T0100_ByteCode01.java
  #13 = NameAndType        #4:#5          // "<init>":()V
  #14 = Utf8               com/mashibing/jvm/c1_bytecode/T0100_ByteCode01
  #15 = Utf8               java/lang/Object
{
  public com.mashibing.jvm.c1_bytecode.T0100_ByteCode01();
    descriptor: ()V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 3: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/mashibing/jvm/c1_bytecode/T0100_ByteCode01;
}
SourceFile: "T0100_ByteCode01.java"
```
Class文件设计的特别紧凑，都没有分隔符，所以再设计编程语言的时候，只要编译成class文件就好。aload_0代表
把本地变量表里的第0号放到栈里，局部变量表里的第0项，只要不是静态的，永远都是this，他会被扔到操作数栈里。
invokespecial是调用this的构造方法。JVM汇编指令中只有8条是原子性的，连long l = 0L; 和
double f = 0.0f;都不是原子性的，他是由ldc和putfield两条指令构成，完全有可能被打断. 加了volatile，
对于long和double的读写就是原子性的了，这里说的原子性是赋值的时候原子性，每一条JVM汇编是不是原子性的。
JVM的接口要求JVM的实现见到volatile修饰的long和double要实现原子性，原来是总线锁实现，现在是MESI实现。
volatile long 在虚拟机底层会当成原子性来处理


## 3：类编译-加载-初始化

hashcode
锁的信息（2位 四种组合）
GC信息（年龄）
如果是数组，数组的长度

## 4：JMM

new Cat()
pointer -> Cat.class
寻找方法的信息

## 5：对象

1：句柄池 （指针池）间接指针，节省内存
2：直接指针，访问速度快

## 6：GC基础知识

栈上分配
TLAB（Thread Local Allocation Buffer）
Old
Eden
老不死 - > Old

## 7：GC常用垃圾回收器

new Object()
markword          8个字节
类型指针           8个字节
实例变量           0
补齐                  0		
16字节（压缩 非压缩）
Object o
8个字节 
JVM参数指定压缩或非压缩

