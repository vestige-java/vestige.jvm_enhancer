/*
 * Copyright 2017 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gaellalire.vestige.jvm_enhancer.runtime.windows;

import java.util.Collection;
import java.util.IdentityHashMap;

import fr.gaellalire.vestige.core.function.Function;

/**
 * @author Gael Lalire
 */
public final class WindowsShutdownHook {

    private WindowsShutdownHook() {
    }

    private static IdentityHashMap<Thread, Boolean> hooks = new IdentityHashMap<Thread, Boolean>();

    public static final Function<Thread, Void, RuntimeException> ADD_SHUTDOWN_HOOK_FUNCTION = new Function<Thread, Void, RuntimeException>() {
        @Override
        public Void apply(final Thread thread) throws RuntimeException {
            addShutdownHook(thread);
            return null;
        }
    };

    public static final Function<Thread, Void, RuntimeException> REMOVE_SHUTDOWN_HOOK_FUNCTION = new Function<Thread, Void, RuntimeException>() {
        @Override
        public Void apply(final Thread thread) throws RuntimeException {
            removeShutdownHook(thread);
            return null;
        }
    };

    public static void init(final String libraryPath) {
        System.load(libraryPath);
        hooks = new IdentityHashMap<Thread, Boolean>();
        nativeRegister();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                runHooks();
            }
        });
    }

    private static native void nativeRegister();

    public static synchronized void addShutdownHook(final Thread hook) {
        if (hooks == null) {
            throw new IllegalStateException("Shutdown in progress");
        }

        if (hook.isAlive()) {
            throw new IllegalArgumentException("Hook already running");
        }

        if (hooks.containsKey(hook)) {
            throw new IllegalArgumentException("Hook previously registered");
        }

        hooks.put(hook, Boolean.TRUE);
    }

    public static synchronized boolean removeShutdownHook(final Thread hook) {
        if (hooks == null) {
            throw new IllegalStateException("Shutdown in progress");
        }

        if (hook == null) {
            throw new NullPointerException();
        }

        return hooks.remove(hook) != null;
    }

    private static Collection<Thread> threads;

    private static void runHooks() {
        synchronized (WindowsShutdownHook.class) {
            if (hooks != null) {
                threads = hooks.keySet();
                hooks = null;
                for (Thread hook : threads) {
                    hook.start();
                }
            }
        }
        for (Thread hook : threads) {
            try {
                hook.join();
            } catch (InterruptedException x) {
                // nothing
            }
        }
    }

}
