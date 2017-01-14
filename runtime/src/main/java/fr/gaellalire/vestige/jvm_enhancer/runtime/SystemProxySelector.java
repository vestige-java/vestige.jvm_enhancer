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

package fr.gaellalire.vestige.jvm_enhancer.runtime;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;

import com.btr.proxy.search.ProxySearch;
import com.btr.proxy.search.ProxySearch.Strategy;

import fr.gaellalire.vestige.core.StackedHandler;

/**
 * @author Gael Lalire
 */
public class SystemProxySelector extends ProxySelector implements StackedHandler<ProxySelector> {

    private ProxySelector proxySelector;

    public static final List<Proxy> NO_PROXY_LIST = Collections.singletonList(Proxy.NO_PROXY);

    public SystemProxySelector() {
        ProxySearch proxySearch = new ProxySearch();
        proxySearch.addStrategy(Strategy.JAVA);
        proxySearch.addStrategy(Strategy.OS_DEFAULT);
        proxySearch.addStrategy(Strategy.ENV_VAR);
        proxySelector = proxySearch.getProxySelector();
    }

    @Override
    public List<Proxy> select(final URI uri) {
        if (proxySelector == null) {
            return NO_PROXY_LIST;
        }
        Thread currentThread = Thread.currentThread();
        ClassLoader contextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(SystemProxySelector.class.getClassLoader());
        try {
            return AccessController.doPrivileged(new PrivilegedAction<List<Proxy>>() {
                @Override
                public List<Proxy> run() {
                    return proxySelector.select(uri);
                }
            });
        } finally {
            currentThread.setContextClassLoader(contextClassLoader);
        }
    }

    @Override
    public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
        if (proxySelector == null) {
            return;
        }
        Thread currentThread = Thread.currentThread();
        ClassLoader contextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(SystemProxySelector.class.getClassLoader());
        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    proxySelector.connectFailed(uri, sa, ioe);
                    return null;
                }
            });
        } finally {
            currentThread.setContextClassLoader(contextClassLoader);
        }
    }

    private ProxySelector nextHandler;

    public ProxySelector getNextHandler() {
        return nextHandler;
    }

    public void setNextHandler(final ProxySelector nextHandler) {
        this.nextHandler = nextHandler;
    }

}
