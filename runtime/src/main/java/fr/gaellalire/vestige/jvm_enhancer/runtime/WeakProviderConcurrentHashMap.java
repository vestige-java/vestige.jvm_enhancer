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

package fr.gaellalire.vestige.jvm_enhancer.runtime;

import java.lang.reflect.Field;
import java.security.Provider;
import java.util.AbstractMap;
import java.util.Properties;
import java.util.Set;

/**
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @author Gael Lalire
 */
public class WeakProviderConcurrentHashMap<K, V> extends AbstractMap<K, V> {

    private WeakConcurrentHashMap<ProviderEntry, V> weakMap = new WeakConcurrentHashMap<ProviderEntry, V>();

    @Override
    public V get(final Object key) {
        return weakMap.get(new ProviderEntry(key));
    }

    @Override
    public V put(final K key, final V value) {
        for (Field field : key.getClass().getDeclaredFields()) {
            if (field.getType() != Provider.class) {
                continue;
            }
            try {
                ProviderEntry providerEntry = new ProviderEntry(key);
                Object object;
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                    try {
                        object = field.get(key);
                    } finally {
                        field.setAccessible(false);
                    }
                } else {
                    object = field.get(key);
                }
                Field declaredField = Properties.class.getDeclaredField("defaults");
                if (!declaredField.isAccessible()) {
                    declaredField.setAccessible(true);
                }
                while (true) {
                    Object nextObject = declaredField.get(object);
                    if (nextObject == null) {
                        // Provider has now a strong reference to providerEntry
                        // so providerEntry cannot be GC before Provider
                        declaredField.set(object, new KeepReferenceProperties(providerEntry));
                        break;
                    }
                    object = nextObject;
                }

                return weakMap.put(providerEntry, value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return weakMap.size();
    }

}
