package com.github.akurilov.concurrent.coroutine.example;

import com.github.akurilov.concurrent.coroutine.Coroutine;
import com.github.akurilov.concurrent.coroutine.CoroutinesExecutor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by andrey on 23.08.17.
 */
public class Main {

	public static void main(final String... args)
	throws InterruptedException, IOException {

		final CoroutinesExecutor executor = new CoroutinesExecutor();
		final Coroutine helloCoroutine = new HelloWorldCoroutine(executor);
		helloCoroutine.start();
		TimeUnit.SECONDS.sleep(10);
		helloCoroutine.close();
	}
}
