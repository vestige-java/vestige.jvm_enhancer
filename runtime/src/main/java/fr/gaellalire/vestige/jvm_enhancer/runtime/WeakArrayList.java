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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @param <E> item type
 * @author Gael Lalire
 */
public class WeakArrayList<E> extends ArrayList<E> {

    private ArrayList<WeakReference<E>> weakReferences = new ArrayList<WeakReference<E>>();

    private static final long serialVersionUID = 1L;

    private E gcValue;

    public WeakArrayList(final E gcValue) {
        this.gcValue = gcValue;
    }

    public List<E> createStrongList() {
        List<E> sList = new ArrayList<E>(weakReferences.size());
        Iterator<WeakReference<E>> iterator = weakReferences.iterator();
        while (iterator.hasNext()) {
            WeakReference<E> next = iterator.next();
            E e = next.get();
            if (e == null) {
                iterator.remove();
            } else {
                sList.add(e);
            }
        }
        return sList;
    }

    public void expunge() {
        Iterator<WeakReference<E>> iterator = weakReferences.iterator();
        while (iterator.hasNext()) {
            WeakReference<E> next = iterator.next();
            if (next.get() == null) {
                iterator.remove();
            }
        }
    }

    @Override
    public int size() {
        expunge();
        return weakReferences.size();
    }

    @Override
    public E get(final int index) {
        E e = weakReferences.get(index).get();
        if (e != null) {
            return e;
        }
        return gcValue;
    }

    @Override
    public boolean add(final E e) {
        weakReferences.add(new WeakReference<E>(e));
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        for (E e : c) {
            add(e);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        WeakArrayList<E> weakArrayList = (WeakArrayList<E>) super.clone();
        weakArrayList.weakReferences = (ArrayList<WeakReference<E>>) weakReferences.clone();
        return weakArrayList;
    }

}
