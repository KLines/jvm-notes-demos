package com.mashibing.jvm.c4_RuntimeDataAreaAndInstructionSet;

public class Hello_01 {
    public static void main(String[] args) {
        int i = 100;
    }

    public void m1() {
        int i = 200;
    }  // sipush，short入栈，因为大于127了

    public void m2(int k) {
        int i = 300;
    }  // istore_2了，因为非静态方法多了个this

    public void add(int a, int b) {
        int c = a + b;
    }  // 局部变量表0：this，1：a，2：b，3：c

    public void m3() {
        Object o = null;
    }

    public void m4() {
        Object o = new Object();
    }


}
