# 使用JavaAgent测试Object的大小

作者：马士兵 http://www.mashibing.com

## 对象大小（64位机）

### 观察虚拟机配置

java -XX:+PrintCommandLineFlags -version

### 普通对象

1. 对象头：markword  8个字节（64位机器） 
2. ClassPointer指针：-XX:+UseCompressedClassPointers 为4字节 不开启为8字节，只想他是那个Class的对象（比如 Object.class）
3. 实例数据
   1. 引用类型：-XX:+UseCompressedOops 为4字节 不开启为8字节 
      Oops Ordinary Object Pointers
4. Padding对齐，8的倍数

### 数组对象

1. 对象头：markword 8个字节（64位机器） 
2. ClassPointer指针同上
3. 数组长度：4字节 (为什么数组最大长度的上限是2^31?因为4个字节,32位所表示的最大的有符号整数的数值是2^31)
4. 数组数据
5. 对齐 8的倍数

对象头这8个字节具体包括：上锁（无锁和偏向是01，轻量级锁是00，重量级锁是10，会调用内核）和GC（11）（对象被回收了多少次了、分代的年龄）
不同的状态下，64位中不同的bit代表的意义不一样

## 实验

1. 新建项目ObjectSize （1.8）

2. 创建文件ObjectSizeAgent（Agent可以在类被load到内存的时候截获她，然后获知她的大小）

   ```java
   package com.mashibing.jvm.agent;
   
   import java.lang.instrument.Instrumentation;
   
   public class ObjectSizeAgent {
       private static Instrumentation inst;
   
       public static void premain(String agentArgs, Instrumentation _inst) {
           inst = _inst;
       }
   
       public static long sizeOf(Object o) {
           return inst.getObjectSize(o);
       }
   }
   ```

3. src目录下创建META-INF/MANIFEST.MF

   ```java
   Manifest-Version: 1.0
   Created-By: mashibing.com
   Premain-Class: com.mashibing.jvm.agent.ObjectSizeAgent
   ```

   注意Premain-Class这行必须是新的一行（回车 + 换行），确认idea不能有任何错误提示

4. 打包jar文件

5. 在需要使用该Agent Jar的项目中引入该Jar包
   project structure - project settings - library 添加该jar包

6. 运行时需要该Agent Jar的类，加入参数：

   ```java
   -javaagent:C:\work\ijprojects\ObjectSize\out\artifacts\ObjectSize_jar\ObjectSize.jar
   ```

7. 如何使用该类：

   ```java
   ​```java
      package com.mashibing.jvm.c3_jmm;
      
      import com.mashibing.jvm.agent.ObjectSizeAgent;
      
      public class T03_SizeOfAnObject {
          public static void main(String[] args) {
              System.out.println(ObjectSizeAgent.sizeOf(new Object()));
              System.out.println(ObjectSizeAgent.sizeOf(new int[] {}));
              System.out.println(ObjectSizeAgent.sizeOf(new P()));
          }
      
          private static class P {
                              //8 _markword
                              //4 _oop指针
              int id;         //4
              String name;    //4
              int age;        //4
      
              byte b1;        //1
              byte b2;        //1
      
              Object o;       //4
              byte b3;        //1
      
          }
      }
   ```

## Hotspot开启内存压缩的规则（64位机）

1. 4G以下，直接砍掉高32位
2. 4G - 32G，默认开启内存压缩 ClassPointers Oops
3. 32G，压缩无效，使用64位
   内存并不是越大越好（^-^）

## IdentityHashCode的问题

回答白马非马的问题：

当一个对象计算过identityHashCode之后，不能进入偏向锁状态
因为对象的hashcode会占据线程ID和Epoch的那几位的位置

https://cloud.tencent.com/developer/article/1480590
 https://cloud.tencent.com/developer/article/1484167

https://cloud.tencent.com/developer/article/1485795

https://cloud.tencent.com/developer/article/1482500

## 对象定位

•https://blog.csdn.net/clover_lily/article/details/80095580

T t = new T();怎么通过t这个引用找到new出来的T对象的？有两种方式：

1. 句柄池：t只想一个对象，对象里有两个指针，一个指向真实的对象，两一个指向它的Class对象 （GC算法效率高，三色算法）
2. 直接指针：直接指向真实对象，然后通过其ClassPointer指向Class对象 （Hotspot用这一种，效率比较高）