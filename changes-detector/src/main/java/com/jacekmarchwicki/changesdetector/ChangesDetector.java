/*
 * Copyright 2016 Jacek Marchwicki <jacek.marchwicki@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.jacekmarchwicki.changesdetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import static com.jacekmarchwicki.changesdetector.internal.Preconditions.checkNotNull;


/**
 * Detector for adapter items that can find what was changed and call recycler adapter methods
 *
 * @param <T> type of items to detect
 */
public class ChangesDetector<T, H> {

    private static final int ACTION_NONE = 0;
    private static final int ACTION_INSERT = 1;
    private static final int ACTION_REMOVE = 2;
    private static final int ACTION_CHANGED = 3;

    /**
     * Interface that is already implemented by RecyclerView
     */
    public interface ChangesAdapter {

        void notifyItemRangeInserted(int start, int count);

        void notifyItemRangeChanged(int start, int count);

        void notifyItemRangeRemoved(int start, int count);

        void notifyItemMoved(int fromPosition, int toPosition);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private H[] mItems = (H[]) new Object[0];
    @Nonnull
    private final Detector<T, H> mDetector;

    /**
     * Created {@link ChangesDetector} with {@link ChangesDetector.Detector}
     *
     * @param detector detector
     */
    public ChangesDetector(@Nonnull Detector<T, H> detector) {
        mDetector = checkNotNull(detector);
    }

    public interface Detector<T, H> {

        @Nonnull
        H apply(@Nonnull T item);

        boolean matches(@Nonnull H item, @Nonnull H newOne);

        boolean same(@Nonnull H item, @Nonnull H newOne);
    }

    private int indexOfItem(@Nonnull List<H> list,
                            int start,
                            @Nonnull H search) {
        for (int i = start; i < list.size(); i++) {
            H item = list.get(i);
            if (mDetector.matches(item, search)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Inform adapter about new data
     *
     * @param adapter adapter to be informed about changes
     * @param values  items for adapter
     * @param force   true if you need to force all data reload
     */
    public void newData(@Nonnull ChangesAdapter adapter,
                        @Nonnull List<T> values,
                        boolean force) {
        checkNotNull(adapter);
        checkNotNull(values);

        final H[] list = apply(values);

        final ArrayList<H> objects = new ArrayList<H>(mItems.length);
        Collections.addAll(objects, mItems);

        clearState();

        int offset = 0;
        int successPosition = 0;
        for (; successPosition < list.length; ) {
            final H item = list[successPosition];
            final int i = indexOfItem(objects, offset, item);
            if (i < 0) {
                executeAction(adapter, ACTION_INSERT, offset);
                objects.add(offset, item);
                offset += 1;
                successPosition += 1;
            } else if (i == offset) {
                if (force || !mDetector.same(item, objects.get(offset))) {
                    executeAction(adapter, ACTION_CHANGED, offset);
                } else {
                    executeAction(adapter, ACTION_NONE, -1);
                }
                offset += 1;
                successPosition += 1;
            } else {
                final H first = objects.get(offset);
                if (existInList(list, successPosition + 1, first)) {
                    executeAction(adapter, ACTION_NONE, -1);

                    adapter.notifyItemMoved(i, offset);
                    if (force || !mDetector.same(item, objects.get(i))) {
                        executeAction(adapter, ACTION_CHANGED, offset);
                    }
                    objects.add(offset, objects.remove(i));
//                    Collections.swap(objects, i, offset);
                    offset += 1;
                    successPosition += 1;
                } else {
                    executeAction(adapter, ACTION_REMOVE, offset);
                    objects.remove(0);
                }
            }
        }
        executeAction(adapter, ACTION_NONE, -1);

        final int realSize = objects.size();
        if (realSize > list.length) {
            adapter.notifyItemRangeRemoved(offset, realSize - list.length);
            objects.clear();
        }

        mItems = list;
    }


    private int lastPosition;
    private int count;
    private int lastAction;

    private void clearState() {
        lastPosition = 0;
        count = 0;
        lastAction = ACTION_NONE;
    }

    private void executeAction(@Nonnull ChangesAdapter adapter, int currentAction, int currentPosition) {
        if (lastAction != currentAction) {
            if (lastAction == ACTION_REMOVE) {
                adapter.notifyItemRangeRemoved(lastPosition, count);
            } else if (lastAction == ACTION_CHANGED) {
                adapter.notifyItemRangeChanged(lastPosition, count);
            } else if (lastAction == ACTION_INSERT) {
                adapter.notifyItemRangeInserted(lastPosition, count);
            }
            lastAction = currentAction;
            lastPosition = currentPosition;
            count = 1;
        } else {
            count += 1;
        }
    }

    private boolean existInList(H[] list, int start, H search) {
        for (int i = start, listLength = list.length; i < listLength; i++) {
            final H item = list[i];
            if (mDetector.matches(item, search)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private H[] apply(@Nonnull List<T> values) {
        @SuppressWarnings("unchecked")
        final H[] result = (H[]) new Object[values.size()];
        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            result[i] = mDetector.apply(value);
        }

        return result;
    }

}