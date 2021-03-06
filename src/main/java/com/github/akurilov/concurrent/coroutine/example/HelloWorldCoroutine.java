package com.github.akurilov.concurrent.coroutine.example;

import com.github.akurilov.concurrent.coroutine.CoroutineBase;
import com.github.akurilov.concurrent.coroutine.CoroutinesExecutor;

import java.io.IOException;

/**
 * Created by andrey on 23.08.17.
 */
public class HelloWorldCoroutine
extends CoroutineBase {

	public HelloWorldCoroutine(final CoroutinesExecutor executor) {
		super(executor);
	}

	@Override
	protected void invokeTimed(long startTimeNanos) {
		System.out.println("Hello world!");
	}

	@Override
	protected void doClose()
	throws IOException {

	}
}
