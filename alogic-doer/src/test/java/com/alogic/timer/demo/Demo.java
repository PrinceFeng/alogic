package com.alogic.timer.demo;

import com.alogic.timer.Scheduler;
import com.alogic.timer.ThreadPoolTaskCommitter;
import com.alogic.timer.matcher.Interval;
import com.alogic.timer.matcher.Once;

public class Demo {

	public static void main(String[] args) {
		Scheduler scheduler = new Scheduler.Simple();
		scheduler.setTaskCommitter(new ThreadPoolTaskCommitter());
		
		scheduler.schedule("testOnce", new Once(), new Runnable(){
			public void run() {
				System.out.println("This will be scheduled once.");
			}
		});
		
		scheduler.schedule("testMore", new Interval(1000), new Runnable(){
			public void run() {
				System.out.println("testMore.");
			}
		});
		
		scheduler.start();
		
		for (int i = 0 ; i < 20 ; i ++){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
		
		scheduler.stop();
		
		
	}

}