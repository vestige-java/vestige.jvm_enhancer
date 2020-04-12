/*
 * Copyright 2020 The Apache Software Foundation.
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

package fr.gaellalire.vestige.jvm_enhancer.runtime.test;

import java.security.Provider;

import org.junit.Assert;
import org.junit.Test;

import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakProviderConcurrentHashMap;

/**
 * @author Gael Lalire
 */
public class TestWeakProviderConcurrentHashMap {

    private static final class IdentityWrapper {
        final Provider obj;

        int hashCode;

        IdentityWrapper(final Provider obj, final int hashCode) {
            this.obj = obj;
            this.hashCode = hashCode;
        }

        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdentityWrapper)) {
                return false;
            }
            return (this.obj == ((IdentityWrapper) o).obj);
        }

        public int hashCode() {
            return hashCode;
        }
    }

    private class MyProvider extends Provider {

        public static final String PROVIDER_NAME = "TESTPRV";

        private static final double PROVIDER_VERSION = 1.0010;

        private static final String PROVIDER_INFO = "Test Provider Version 1.0.10";

        protected MyProvider() {
            super(PROVIDER_NAME, PROVIDER_VERSION, PROVIDER_INFO);
        }

    }

    @Test
    public void testNoGC() throws Exception {
        WeakProviderConcurrentHashMap<IdentityWrapper, Boolean> map = new WeakProviderConcurrentHashMap<IdentityWrapper, Boolean>();
        MyProvider myProvider = new MyProvider();
        map.put(new IdentityWrapper(myProvider, 3), Boolean.TRUE);

        for (int i = 0; i < 200; i++) {
            System.gc();
        }

        Assert.assertEquals(Boolean.TRUE, map.get(new IdentityWrapper(myProvider, 3)));
    }

    @Test
    public void testGC() throws Exception {
        WeakProviderConcurrentHashMap<IdentityWrapper, Boolean> map = new WeakProviderConcurrentHashMap<IdentityWrapper, Boolean>();

        MyProvider myProvider0 = new MyProvider();
        map.put(new IdentityWrapper(myProvider0, 3), Boolean.TRUE);

        MyProvider myProvider = new MyProvider();
        map.put(new IdentityWrapper(myProvider, 3), Boolean.TRUE);

        MyProvider myProvider2 = new MyProvider();
        map.put(new IdentityWrapper(myProvider2, 3), Boolean.TRUE);

        Assert.assertEquals(3, map.size());

        myProvider = null;

        for (int i = 0; i < 200; i++) {
            System.gc();
        }

        // one of these 2 call should clean myProvider (same hash, one inserted before, one inserted after)
        Assert.assertEquals(Boolean.TRUE, map.get(new IdentityWrapper(myProvider2, 3)));
        Assert.assertEquals(Boolean.TRUE, map.get(new IdentityWrapper(myProvider0, 3)));

        Assert.assertEquals(2, map.size());
    }

}
