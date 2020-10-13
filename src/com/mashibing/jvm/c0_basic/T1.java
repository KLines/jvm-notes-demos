package com.mashibing.jvm.c0_basic;

public class T1 {
	public static void main(String[] args) {
		new Thread(()->{
			while (true) {
				T2 t2 = new T2();
				t2.m();
			}
		}
		).start();
	}
}
