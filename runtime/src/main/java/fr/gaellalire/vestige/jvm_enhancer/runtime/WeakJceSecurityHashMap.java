/*
 * This file is part of Vestige.
 *
 * Vestige is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Vestige is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Vestige.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.gaellalire.vestige.jvm_enhancer.runtime;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Gael Lalire
 * @param <K> key type
 * @param <V> value type
 */
public class WeakJceSecurityHashMap<K, V> extends HashMap<K, V> {

    private static final long serialVersionUID = -7590280733376745723L;

    private Map<K, WeakReference<V>> weakMap = new WeakHashMap<K, WeakReference<V>>();

    @Override
    public synchronized V get(final Object key) {
        WeakReference<V> weakReference = weakMap.get(key);
        if (weakReference == null) {
            return null;
        }
        return weakReference.get();
    }

    @Override
    public synchronized V put(final K key, final V value) {
        WeakReference<V> old = weakMap.put(key, new WeakReference<V>(value));
        if (old == null) {
            return null;
        }
        return old.get();
    }

}
