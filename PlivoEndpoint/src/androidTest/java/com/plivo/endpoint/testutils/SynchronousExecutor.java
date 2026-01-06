package com.plivo.endpoint.testutils;


import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

public class SynchronousExecutor implements Executor {
    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }
}
