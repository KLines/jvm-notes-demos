package com.mashibing.jvm.c0_basic;

public class T2 {

	public void m() {
		System.out.println(2);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
