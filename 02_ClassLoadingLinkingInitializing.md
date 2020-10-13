## 3：类加载-初始化 （基于 JDK 1.8）

1. 加载过程
   JVM里面所有的类都是由类加载器 ClassLoader 来加载到内存的。
   类被加载到内存之后，生成了两块内容：1。硬盘中的文件原封不动地写入内存的meta space。2。Class类的对象，指向这个在内存中的二进制文件。其他的对象就通过这个
   Class类的对象去访问这个内存中的Class文件。Class类的对象是Hotspot中 C++ 代码 load的过程之中给弄出来的，未必是调用了new关键字
   1. Loading
      Bootstrap类加载器：加载lib/rt.jar charset.jar 等核心类，C++实现。Extension（JDK 12中是PlatformClassLoader）类加载器：加载扩展jar包：jre/lib/ext*.jar，或由 
      -Djava.ext.dirs指定. App类加载器：加载classpath指定的内容。 CustomClassLoader自定义ClassLoader。这4个ClassLoader之间并不是继承关系
      getClassLoader就是找到是谁把这个Class类的对象load进内存的，父ClassLoader先load子ClassLoader，然后子Classloader再去load别的类。
      可以一直查找加载器的加载器是谁，加载器的加载器既不一定是他的父类，也不一定是他的parent属性指向的那个对象（parent属性指向的那个对象才是他的"父加载器"，
      这个"父加载器"是工序层面上的，而不是extends的那个类）一直找到BootstrapClassLoader之后，会返回null，因为它是C++代码写的，Java里面没有一个Class和
      它对应。Bootstrap加载器是C++实现的一个模块  
      
      加载过程：loadClass方法调用，假设一个Class文件，假如有自定义的CustomClassLoader，就先尝试找它（native方法：findLoadedClass0），它里面有个小缓存，记录了已经
      load的Class，如果已经加载，就返回，无需加载第二遍了，如果还没加载过，则并不是立即加载，而是找AppClassLoader，后者也去查他的缓存，已加载就返回，没加载就
      找他的父加载器，直到 Bootstrap ClassLoader，如果他也没加载过，再查一下是不是Bootstrap ClassLoader的负责范围，如果是，则加载，如果不是再继续
      把加载这件事交回给子加载器，最后转了一圈委托回来再由这个CustomClassLoader加载。如果最后加载成功了，则没问题；否则，抛出ClassNotFoundException。
      以上过程就叫做"双亲委派"（这里翻译有点问题）Classloader是反射的基石，反射就是借助于Class对象来访问Classloader load到内存中的二进制文件 什么时候会需要自己去加载？
      Spring 动态代理的时候，生成的动态代理一个新的Class，用的时候实际上Spring已经load到内存里了。JReBel热部署。
      
      1. 双亲委派，*主要出于安全来考虑*。加入任何一个自定义的CustomClassloader都可以把class文件load到内存的话，则可以load 自定义的一个java.lang.String，
        这样就可以覆盖JDK的String类，再load到内存，再打包成一个类库交给客户。客户在输入密码的时候，应该会把密码存成一个String类型的对象，而这个String类
        的对象里面可以写个发邮件的程序暴露其密码。次要问题是资源浪费。当转了一圈被委派回来的时候，会调用findClass方法，而在ClassLoader这个类里面，此方法是个protected的，
        而且里面直接throw了ClassNotFoundException，可见这是个模版方法，自定义的子类要实现它。面试的时候举例模版方法的时候，可以用这个例子. 重写ClassLoader类的
        loadClass方法可以破坏双亲委派机制，有两次曾经就是被破坏了，但是是被JDK自己破坏的。Spring和tomcat都有自己的Classloader，这样就可以load自己指定目录（而不是classpath下面）
        下的Class文件了。双亲委派就像一条责任链
      
      2. LazyLoading （很繁琐，但是没人考这个，扩展着玩的，别深究）五种情况
      
         1. –new getstatic putstatic invokestatic指令，访问final变量除外
      
            –java.lang.reflect对类进行反射调用时
      
            –初始化子类的时候，父类首先初始化
      
            –虚拟机启动时，被执行的主类必须初始化
      
            –动态语言支持java.lang.invoke.MethodHandle解析的结果为REF_getstatic REF_putstatic REF_invokestatic的方法句柄时，该类必须初始化
      
      3. ClassLoader的源码
      
         1. findInCache -> parent.loadClass -> findClass()
      
      4. 自定义类加载器
      
         1. extends ClassLoader
         2. overwrite findClass() 里面应该会调用 -> defineClass(byte[] -> Class clazz)
         3. 加密
         4. <font color=red>第一节课遗留问题：parent是如何指定的，打破双亲委派，学生问题桌面图片</font>
            1. 用super(parent)指定
            2. 双亲委派的打破
               1. 如何打破：重写ClassLoader.loadClass(),因为它里面定义了先找自己有没有加载过这个Class然后再去parent成员递归地中找
               2. 何时打破过？
                  1. JDK1.2之前，自定义ClassLoader都必须重写loadClass()
                  2. ThreadContextClassLoader可以实现基础类调用实现类代码，通过thread.setContextClassLoader指定
                  3. 热启动，热部署
                     1. osgi tomcat 都有自己的模块指定classloader（可以加载同一类库的不同版本）.热部署（加载）的时候就不去检查
                        原来是否加载过了，可以二话不说直接重新加载。期间ClassLoader也要new新的、换引用的指向，一个Class的ClassLoader
                        被回收之后，他们在MetaSpace中也呆不长，也会被回收
      
      5. 混合执行 编译执行 解释执行
         Java是混合模式：混合使用解释器和热点编译（JIT）。起始阶段采用解释执行。热点代码检测：多次被调用的方法（方法计数器：检测方法执行频率）
         多次被调用的循环（循环计数器：检测循环执行频率）进行编译。 参数：-Xmixed 默认为混合模式开始解释执行，启动速度比较快，对热点代码进行解释
         和编译； -Xint 使用解释模式，启动很快执行稍慢；-Xcomp使用纯编译模式，执行很快启动很慢。编译和混合差不多，都很快，但是纯解释就很慢很慢
         注：C语言编译玩的格式叫做本地（native）代码，Windows下是exe，Linux下是elf
      
         1. 检测热点代码：-XX:CompileThreshold = 10000
      
   2. Linking 
      1. Verification
         1. 验证文件是否符合JVM规定
      2. Preparation
         1. 静态成员变量赋默认值: static int i = 8; 在这里先赋值成0这个默认值，而不是8
      3. Resolution
         1. 将类、方法、属性等符号引用解析为直接引用
            常量池中的各种符号引用解析为指针、偏移量等内存地址的直接引用: 很多是动态绑定，new出来之后才知道指向哪里
      
   3. Initializing
   
      1. 调用类初始化代码 <clinit>，给静态成员变量赋初始值static int i = 8;这句话这里赋值为8
      2. 调用静态代码块
   
2. 小总结：

   1. load -> 默认值（preparation） -> 初始值（Initializing）
   2. new - 申请内存 -> 默认值 -> 初始值。其实是跟累的加载过程类似，这就解释了为什么不复制的成员变量默认是0，或者null